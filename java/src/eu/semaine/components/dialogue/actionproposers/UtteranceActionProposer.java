/**
 * Copyright (C) 2009 University of Twente. All rights reserved.
 * Use is subject to license terms -- see license.txt.
 */

package eu.semaine.components.dialogue.actionproposers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.jms.JMSException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import eu.semaine.components.Component;
import eu.semaine.components.dialogue.datastructures.AgentUtterance;
import eu.semaine.components.dialogue.datastructures.DialogueAct;
import eu.semaine.components.dialogue.datastructures.EmotionEvent;
import eu.semaine.datatypes.stateinfo.AgentStateInfo;
import eu.semaine.datatypes.stateinfo.DialogStateInfo;
import eu.semaine.datatypes.stateinfo.UserStateInfo;
import eu.semaine.datatypes.xml.BML;
import eu.semaine.datatypes.xml.FML;
import eu.semaine.datatypes.xml.SSML;
import eu.semaine.datatypes.xml.SemaineML;
import eu.semaine.exceptions.MessageFormatException;
import eu.semaine.jms.message.SEMAINEAgentStateMessage;
import eu.semaine.jms.message.SEMAINEMessage;
import eu.semaine.jms.message.SEMAINEUserStateMessage;
import eu.semaine.jms.message.SEMAINEXMLMessage;
import eu.semaine.jms.receiver.AgentStateReceiver;
import eu.semaine.jms.receiver.UserStateReceiver;
import eu.semaine.jms.receiver.XMLReceiver;
import eu.semaine.jms.sender.FMLSender;
import eu.semaine.jms.sender.StateSender;
import eu.semaine.jms.sender.XMLSender;
import eu.semaine.util.XMLTool;

/**
 * The UtteranceActionProposer determines what to say based on the current context.
 * TODO: uitbreiden, precieze werking beschrijven in details
 * 
 * Input
 * AgentStateReceiver('emaine.data.state.agent')			--> take/release turn messages
 * UserStateReceiver('semaine.data.state.user.behaviour') 	--> user speaking state and detected emotions
 * XMLReceiver('semaine.data.state.context')				--> For context information such as user present and the current character
 * 
 * Output
 * FMLSender('semaine.data.action.candidate.function')		--> utterances to the output modules
 * DialogStateSender('semaine.data.state.dialog')			--> dialog state (speaker & listener)
 * XMLSender('semaine.data.state.context')					--> For context information such as user present and the current character
 * 
 * @author MaatM
 *
 */

public class UtteranceActionProposer extends Component
{	
	/* Agent states */
	public final static int WAITING = 0;	// Waiting for the other person to start speaking
	public final static int LISTENING = 1;	// Listening to the speech of the user
	public final static int SPEAKING = 2;	// The agent is speaking
	
	/* The four characters */
	public final static int POPPY = 1;
	public final static int PRUDENCE = 2;
	public final static int SPIKE = 3;
	public final static int OBADIAH = 4;
	private HashMap<Integer,String> charNames = new HashMap<Integer,String>();
	private HashMap<String,Integer> charNumbers = new HashMap<String,Integer>();
	private HashMap<Integer,Boolean> charHistory = new HashMap<Integer,Boolean>();
	
	/* The current character */
	private int currChar = 1;
	private boolean systemStarted = false;
	
	/* Change character states */
	public final static int NEUTRAL = 0;
	public final static int CHANGE_ASKED = 1;
	public final static int CHAR_SUGGESTED = 2;
	public final static int CHAR_ASKED = 3;
	public int charChangeState = 0;
	public int suggestedChar = 0;
	
	/* Character startup states */
	public final static int INTRODUCED = 1;
	public final static int HOW_ARE_YOU_ASKED = 2;
	public int charStartupState = 0;
	
	/* Thresholds */
	public final static float HIGH_AROUSAL = 0.8f;
	public final static float LOW_AROUSAL = 0.1f;
	public final static long SMALL_UTTERANCE = 3000;
	
	public final static int UTTERANCE_LOOKBACK = 4;
	
	/* Data locations */
	public final static String sentenceDataPath = "/eu/semaine/components/dialog/data/sentences.xml";
	
	/* Senders and Receivers */
	private AgentStateReceiver agentStateReceiver;
	private XMLReceiver userStateReceiver;
	private XMLReceiver contextReceiver;
	private FMLSender fmlSender;
	private StateSender dialogStateSender;
	private XMLSender contextSender;
	
	/* The current state of the agent */
	public int agentSpeakingState = 1;		// 0, 1, or 2
	private long agentSpeakingStateTime = 0;	
	
	/* Turn state of speaker */
	private int userSpeakingState = 1;		// 1 or 2
	private long userSpeakingStateTime = 0;
	
	/* List of detected emotion events (generated by the EmotionInterpreter) */
	private ArrayList<EmotionEvent> detectedEmotions = new ArrayList<EmotionEvent>();
	private int emotionIndex = 0;
	
	/* List of all detected Dialogue Acts (generated by the UtteranceInterpreter) */
	private ArrayList<DialogueAct> detectedDActs = new ArrayList<DialogueAct>();
	private int dActIndex = 0;
	
	/* All agent utterances grouped by utterance-type */
	private HashMap<Integer,HashMap<String,ArrayList<String>>> allUtterances = new HashMap<Integer,HashMap<String,ArrayList<String>>>();
	
	private ArrayList<AgentUtterance> utteranceHistory = new ArrayList<AgentUtterance>();
	private int subjectIndex = 0;
	
	/* The current user */
	private boolean isUserPresent = false;
	private static final String PRESENT = "present";
	private static final String ABSENT = "absent";
	
	/* Random generator */
	private Random rand = new Random();
	
	
	/**
	 * Constructor of UtteranceActionProposer
	 * Initializes the senders and receivers, randomly determines the first character and 
	 * initializes some data
	 * @throws JMSException
	 */
	public UtteranceActionProposer() throws JMSException
	{
		super( "UtteranceActionProposer" );
		
		/* Initialize receivers */
		agentStateReceiver = new AgentStateReceiver( "semaine.data.state.agent" );
		receivers.add( agentStateReceiver );
		userStateReceiver = new XMLReceiver("semaine.data.state.user.behaviour");
		receivers.add(userStateReceiver);
		contextReceiver = new XMLReceiver("semaine.data.state.context");
		receivers.add( contextReceiver );
		
		/* Initialize senders */
		fmlSender = new FMLSender("semaine.data.action.candidate.function", getName());
		senders.add(fmlSender);
		dialogStateSender = new StateSender("semaine.data.state.dialog", "DialogState", getName());
		senders.add(dialogStateSender);
		contextSender = new XMLSender("semaine.data.state.context", "SemaineML", getName());
		senders.add(contextSender);
		
		/* Determine the first character */
		currChar = rand.nextInt(4)+1;
		
		/* Initialize some data */
		initData();
	}
	
	/**
	 * Initializes the utterances of the characters, the character names, and the character history
	 */
	public void initData()
	{
		/* Reads the utterances of all the characters */
		SentenceReader sentenceReader = new SentenceReader( sentenceDataPath );
		if( sentenceReader.readData() ) {
			allUtterances = sentenceReader.getAllUtterances();
		} else {
			// TODO: Log error
		}
		
		/* Set the character name to number conversion maps */
		charNames.put( POPPY, "Poppy" );
		charNames.put( PRUDENCE, "Prudence" );
		charNames.put( SPIKE, "Spike" );
		charNames.put( OBADIAH, "Obadiah" );
		
		charNumbers.put("poppy", POPPY);
		charNumbers.put("prudence", PRUDENCE);
		charNumbers.put("spike", SPIKE);
		charNumbers.put("obadiah", OBADIAH);
		
		/* resets the chat history of the characters (this determines if the characters have spoken
		 * with these characters before in this conversation */
		charHistory.put( POPPY, false );
		charHistory.put( PRUDENCE, false );
		charHistory.put( SPIKE, false );
		charHistory.put( OBADIAH, false );
	}
	
	/**
	 * Checks if the system has started. If it hasn't it will start it by making the first
	 * character introduce itself.
	 */
	public void act() throws JMSException
	{
//		if( !systemStarted && isUserPresent ) {
//			/* Update agent speaking state */
//			agentSpeakingState = SPEAKING;
//			
//			charStartupState = INTRODUCED;
//			sendNewCharacter( currChar );
//			
//			charHistory.put(currChar, true);
//			sendUtterance( pickUtterances("intro_new") );
//			
//			systemStarted = true;
//			
//			/* TEMPORARILY called until the end of the agent utterance is received from the output module */
//			processUtteranceEnd();
//		}
	}
	
	/**
	 * Sets context variables if updates are received.
	 * If it receives the message that the agent should start talking it will determine what to say
	 * and output this.
	 */
	public void react( SEMAINEMessage m ) throws JMSException
	{
		/* Processes User state updates */
		if( m instanceof SEMAINEUserStateMessage ) {
			SEMAINEUserStateMessage um = ((SEMAINEUserStateMessage)m);
			
			/* Updates user speaking state (speaking or silent) */
			setUserSpeakingState( um );
			
			/* Updates detected emotions (valence, arousal, interest) */
			addDetectedEmotions( um );
		}
		
		/* Processes XML updates */
		if( m instanceof SEMAINEXMLMessage ) {
			SEMAINEXMLMessage xm = ((SEMAINEXMLMessage)m);
			
			/* Updated analyzed Dialogue Acts history */
			addDetectedDActs( xm );
			
			/* Updates the current character and the user */
			updateCharacterAndUser( xm );
		}
		
		/* If the TurnTakingInterpreter decides that the agent should speak, determine what to say */
		if( agentShouldSpeak( m ) ) {
			
			/* Update agent speaking state */
			agentSpeakingState = SPEAKING;
			
			AgentUtterance utterance = null;
			
			/* Check if the character change process should be started or continued */
			utterance = manageCharChange();
			if( utterance == null ) {
				/* Check if the character start process should be started or continued */
				utterance = manageAgentStart();
				if( utterance == null ) {
					/* Get an utterance to say */
					utterance = getResponse();
				}
			}
			
			/* Distribute the chosen utterance */
			sendUtterance( utterance );
			
			/* TEMPORARILY called until the end of the agent utterance is received from the output module */
			processUtteranceEnd();
		}
	}
	
	public void updateCharacterAndUser( SEMAINEXMLMessage xm ) throws JMSException
	{
		Document doc = xm.getDocument();
		Element root = doc.getDocumentElement();
		if (!root.getTagName().equals(SemaineML.E_CONTEXT)) {
			return;
		}
		if (!root.getNamespaceURI().equals(SemaineML.namespaceURI)) {
			throw new MessageFormatException("Unexpected document element namespace: expected '"+SemaineML.namespaceURI+"', found '"+root.getNamespaceURI()+"'");
		}
		
		boolean newUser = false;
		List<Element> users = XMLTool.getChildrenByTagNameNS(root, SemaineML.E_USER, SemaineML.namespaceURI);
		for( Element user : users ) {
			String status = user.getAttribute( SemaineML.A_STATUS );
			if( status.equals(PRESENT) && !isUserPresent ) {
				newUser = true;
				userAppeared();
			} else if( status.equals(ABSENT) && isUserPresent ) {
				userDisappeared();
			}
		}
		
		List<Element> characters = XMLTool.getChildrenByTagNameNS(root, SemaineML.E_CHARACTER, SemaineML.namespaceURI);
		for( Element characterElem : characters ) {
			if( systemStarted ) {
				String charName = characterElem.getAttribute(SemaineML.A_NAME);
				
				charStartupState = INTRODUCED;
				
				/* Update agent speaking state */
				agentSpeakingState = SPEAKING;
				
				currChar = charNumbers.get( charName.toLowerCase() );
				
				if( charHistory.get(currChar) ) {
					sendUtterance( pickUtterances("intro_old") );
				} else {
					charHistory.put(currChar, true);
					sendUtterance( pickUtterances("intro_new") );
				}
				
				/* TEMPORARILY called until the end of the agent utterance is received from the output module */
				processUtteranceEnd();
			}
		}
		
		if( newUser && charStartupState != INTRODUCED ) {
			charStartupState = INTRODUCED;
			
			/* Update agent speaking state */
			agentSpeakingState = SPEAKING;
			
			if( charHistory.get(currChar) ) {
				sendUtterance( pickUtterances("intro_old") );
			} else {
				charHistory.put(currChar, true);
				sendUtterance( pickUtterances("intro_new") );
			}
			
			/* TEMPORARILY called until the end of the agent utterance is received from the output module */
			processUtteranceEnd();
		}
	}
	
	/**
	 * Called when a user is detected in the screen.
	 * TODO: Not yet called, if it is called I should also remove this section from act()
	 * @throws JMSException
	 */
	public void userAppeared() throws JMSException
	{
		isUserPresent = true;
		
		systemStarted = true;
	}
	
	/**
	 * Called when the user disappears from the screen
	 * TODO: Not yet called, goodbye-sentences should also be added to sentences.xml
	 * @throws JMSException
	 */
	public void userDisappeared() throws JMSException
	{
		charStartupState = NEUTRAL;
		isUserPresent = false;
		
		/* Update agent speaking state */
		agentSpeakingState = SPEAKING;
		
		sendUtterance( pickUtterances("goodbye") );
		
		/* resets the chat history of the characters (this determines if the characters have spoken
		 * with these characters before in this conversation */
		charHistory.put( POPPY, false );
		charHistory.put( PRUDENCE, false );
		charHistory.put( SPIKE, false );
		charHistory.put( OBADIAH, false );
		
		systemStarted = false;
	}
	
	/**
	 * Manages the character change process.
	 * If the user is in this process it will determine what the next step is and return 
	 * an AgentUtterance to speak.
	 * If the user is not in the character change process it will return null
	 * @throws JMSException
	 */
	public AgentUtterance manageCharChange() throws JMSException
	{
		/* Make a list of all analyzed Dialogue Acts since the last time the agent talked */
		ArrayList<DialogueAct> recentDActs = new ArrayList<DialogueAct>( detectedDActs.subList(dActIndex, detectedDActs.size()) );
		
		if( charChangeState == NEUTRAL ) {
			/* Determine if the user wants to change the character */
			boolean wantChange = false;
			String targetCharacter = null;
			for( DialogueAct act : recentDActs ) {
				if( act.isChangeSpeaker() ) {
					// User wants to change the speaker
					wantChange = true;
				}
				if( act.getTargetCharacter() != null ) {
					targetCharacter = act.getTargetCharacter();
				}
			}
			
			/* If the use wants to change the system has to determine the next character */
			if( wantChange ) {
				if( targetCharacter != null ) {
					/* If the user already mentioned a new character then take this */
					currChar = charNumbers.get(targetCharacter.toLowerCase());
					charStartupState = INTRODUCED;
					sendNewCharacter( currChar );
					charChangeState = NEUTRAL;
					if( charHistory.get(currChar) ) {
						return pickUtterances("intro_old");
					} else {
						charHistory.put(currChar, true);
						return pickUtterances("intro_new");
					}
				} else {
					/* If the user did not mention a character then either ask for it or propose one */
					if( rand.nextBoolean() ) {
						// Ask for the character
						charChangeState = CHAR_ASKED;
						return pickUtterances("ask_next_character");
					} else {
						// Suggest a character
						charChangeState = CHAR_SUGGESTED;
						suggestedChar = rand.nextInt(4)+1;
						while( suggestedChar == currChar ) {
							suggestedChar = rand.nextInt(4)+1;
						}
						return new AgentUtterance( "change_character", "Do you want to talk to " + charNames.get(suggestedChar) + "?" );
					}
				}
			}
		} else if( charChangeState == CHAR_ASKED ) {
			/* If the system just asked for the next character it will have to determine if a suggestion was made */
			String targetCharacter = null;
			for( DialogueAct act : recentDActs ) {
				if( act.getTargetCharacter() != null ) {
					targetCharacter = act.getTargetCharacter();
				}
			}
			
			if( targetCharacter != null ) {
				/* If the user chose a character then take this one */
				currChar = charNumbers.get(targetCharacter.toLowerCase());
				sendNewCharacter( currChar );
				charStartupState = INTRODUCED;
				charChangeState = NEUTRAL;
				if( charHistory.get(currChar) ) {
					return pickUtterances("intro_old");
				} else {
					charHistory.put(currChar, true);
					return pickUtterances("intro_new");
				}
			} else {
				/* If the user did not choose a character then try to repair it */
				return pickUtterances("repair_ask_next_character");
			}
		} else if( charChangeState == CHAR_SUGGESTED ) {
			/* If the system just suggested a character than it will have to determine if the user
			 * agreed or disagreed with this suggestion */
			boolean agree = false;
			boolean disagree = false;
			for( DialogueAct act : recentDActs ) {
				if( act.isAgree() ) {
					agree = true;
				}
				if( act.isDisagree() ) {
					disagree = true;
				}
			}
			if( !agree && !disagree ) {
				/* If the user did not give any sign try to repair it */
				return pickUtterances("repair_suggest_next_character");
			} else if( agree ) {
				/* If the user agreed to the suggestion then use it */
				currChar = suggestedChar;
				charStartupState = INTRODUCED;
				sendNewCharacter( currChar );
				charChangeState = NEUTRAL;
				if( charHistory.get(currChar) ) {
					return pickUtterances("intro_old");
				} else {
					charHistory.put(currChar, true);
					return pickUtterances("intro_new");
				}
			} else if( disagree ) {
				/* If the user disagreed to the suggestion then ask for the next character */
				charChangeState = CHAR_ASKED;
				return pickUtterances("ask_next_character");
			}
		}
		return null;
	}
	
	/**
	 * Manages the agent startup process
	 * If the user is in this process it will determine what the next step in the process is
	 * and return an AgentUtterance to speak.
	 * If the user is not in this process it will return null.
	 */
	public AgentUtterance manageAgentStart( )
	{
		if( charStartupState == INTRODUCED ) {
			/* If the system just introduced himself then ask how the user feels today */
			charStartupState = HOW_ARE_YOU_ASKED;
			return pickUtterances("intro_how_are_you");
		} else if( charStartupState == HOW_ARE_YOU_ASKED ) {
			/* If the system just asked how the user feels it will ask the user to tell it more */
			charStartupState = NEUTRAL;
			return pickUtterances("intro_tell_me_more");
		}
		return null;
	}
	
	/**
	 * Reads the messages from the TurnTakingInterpreter and decides if the agent should 
	 * start speaking or not.
	 * @param m - the received message
	 * @return - true if the user should speak, false it if shouldn't
	 */
	public boolean agentShouldSpeak( SEMAINEMessage m )
	{
		/* Check if the system has started, if not return false */
		if( !systemStarted ) {
			return false;
		}
		
		if( m instanceof SEMAINEAgentStateMessage ) {
			SEMAINEAgentStateMessage am = (SEMAINEAgentStateMessage)m;
			
			AgentStateInfo agentInfo = am.getState();
			Map<String,String> agentInfoMap = agentInfo.getInfo();
			
			String intention = agentInfoMap.get("intention");
			if( intention != null && intention.equals("speaking") ) {
				if( agentSpeakingState == SPEAKING ) {
					return false;
				} else {
					return true;
				}
			} else {
				return false;
			}
		}
		return false;
	}
	
	/**
	 * Determines what to say based on the context
	 * Currently, the context means: detected arousal, length of the user utterance,
	 * and the history of agent utterances.
	 * @return the AgentUtterance to speak next
	 */
	public AgentUtterance getResponse()
	{
		/* Determine high and low arousal indicators and user utterance length */
		int high_intensity_arousal = 0;
		int low_intensity_arousal = 0;
		long user_utterance_length = meta.getTime() - agentSpeakingStateTime;
		
		for( int i=emotionIndex; i< detectedEmotions.size(); i++ ) {
			EmotionEvent ee = detectedEmotions.get(i);
			if( ee.getType() == EmotionEvent.AROUSAL ) {
				if( ee.getIntensity() > HIGH_AROUSAL ) {
					high_intensity_arousal++;
				} else if( ee.getIntensity() < LOW_AROUSAL ) {
					low_intensity_arousal++;
				}
			}
		}
		
		/* Determine the number of 'tell me more' utterances in this subject */
		int tellMeMoreCounter = 0;
		for( int i=subjectIndex; i<utteranceHistory.size(); i++ ) {
			AgentUtterance utterance = utteranceHistory.get(i);
			if( utterance.getType().equals("tell_me_more") ) {
				tellMeMoreCounter++;
			}
		}
		
		if( high_intensity_arousal-low_intensity_arousal > 0 || (high_intensity_arousal > 0 && low_intensity_arousal == high_intensity_arousal) ) {
			/* If there are high arousal indicators and there are more than low arousal indicators
			 * respond with a 'high arousal utterance' */
			AgentUtterance uttr = pickUtterances( "high_arousal" ) ;
			if( uttr != null ) {
				return uttr;
			}
		} else if( low_intensity_arousal-high_intensity_arousal > 0 ) {
			/* If there are low arousal indicators and there are more than high arousal indicators
			 * respond with a 'low arousal utterance' */
			AgentUtterance uttr = pickUtterances( "low_arousal" ) ;
			if( uttr != null ) {
				return uttr;
			}
		}

		if( tellMeMoreCounter >= 2 ) {
			/* If the number of 'tell me more' utterances is greater than 2
			 * change the subject or change the character */
			if( rand.nextBoolean() ) {
				subjectIndex = utteranceHistory.size()-1;
				return pickUtterances("change_subject");
			} else {
				if( rand.nextBoolean() ) {
					// Ask for the character
					charChangeState = CHAR_ASKED;
					return pickUtterances("ask_next_character");
				} else {
					// Suggest a character
					charChangeState = CHAR_SUGGESTED;
					suggestedChar = rand.nextInt(4)+1;
					while( suggestedChar == currChar ) {
						suggestedChar = rand.nextInt(4)+1;
					}
					return new AgentUtterance( "change_character", "Do you want to talk to " + charNames.get(suggestedChar) + "?" );
				}
			}
		} else if( agentSpeakingState == LISTENING ) {
			/* If the user utterance is smaller than a predefined threshold
			 * respond with a 'tell me more utterance' */
			if( user_utterance_length < SMALL_UTTERANCE ) {
				AgentUtterance uttr = pickUtterances( "tell_me_more" ) ;
				if( uttr != null ) {
					return uttr;
				}
			}
		} else {
			/* If no utterance can be determind pick one of the random utterances */
			AgentUtterance uttr = pickUtterances( "random" ) ;
			if( uttr != null ) {
				return uttr;
			}
		}
		return null;
	}

	/**
	 * Based on the given type of sentence this method tries to find an utterance of that type
	 * that hasn't been said for the last x agent utterances.
	 * @param type - the type of the utterance
	 * @return - the AgentUtterance which includes the utterance type and the utterance itself
	 */
	public AgentUtterance pickUtterances( String type )
	{
		/* Get all utterances of the given type that haven't been used for the last x utterances */
		HashMap<String,ArrayList<String>> utterancesChar = allUtterances.get(currChar);
		ArrayList<String> utterancesType = utteranceCopy( utterancesChar.get(type) );
		
		for( int i=utteranceHistory.size()-1; i>=0; i-- ) {
			AgentUtterance uttr = utteranceHistory.get(i);
			if( utterancesType.contains( uttr.getUtterance() ) ) {
				utterancesType.remove(uttr.getUtterance());
			}
		}
		
		if( utterancesType.size() == 0 ) {
			/* If the list is empty do something else */
			if( !type.equals("ask_next_character") && !type.equals("repair_ask_next_character") && !type.equals("suggest_next_character")&& !type.equals("repair_suggest_next_character") ) {
				charChangeState = CHAR_ASKED;
				return pickUtterances("ask_next_character");
			} else {
				charChangeState = NEUTRAL;
				subjectIndex = utteranceHistory.size()-1;
				return pickUtterances("change_subject");
			}
		} else {
			/* If the list isn't empty randomly pick an utterance from the list */
			return new AgentUtterance( type, utterancesType.get(rand.nextInt(utterancesType.size())) );
		}
	}
	
	/**
	 * Called when the output module messages that the utterance is finished.
	 * Will put the agent state on listening again and send this state around.
	 * TODO: This method isn't called yet!
	 * @throws JMSException
	 */
	public void processUtteranceEnd() throws JMSException
	{	
		agentSpeakingState = LISTENING;
		agentSpeakingStateTime = meta.getTime();
		sendListening();
	}
	
	/**
	 * Sends the given utterance to the output modules.
	 * 
	 * @param utterance
	 * @throws JMSException
	 */
	public void sendUtterance( AgentUtterance utterance ) throws JMSException
	{	
		/* Send utterance to Greta */
		String response = utterance.getUtterance();
		
		String id = "s1";
		
		Document doc = XMLTool.newDocument("fml-apml", null, FML.version);
		Element root = doc.getDocumentElement();

		Element bml = XMLTool.appendChildElement(root, BML.E_BML, BML.namespaceURI);
		bml.setAttribute(BML.A_ID, "bml1");
		Element fml = XMLTool.appendChildElement(root, FML.E_FML, FML.namespaceURI);
		fml.setAttribute(FML.A_ID, "fml1");
		Element speech = XMLTool.appendChildElement(bml, BML.E_SPEECH);
		speech.setAttribute(BML.A_ID, id);
		speech.setAttribute(BML.E_TEXT, response);
		speech.setAttribute(BML.E_LANGUAGE, "en-GB");

		//speech.setTextContent(response);
		
		int counter=1;
		for( String word : response.split(" ") ) {
			Element mark = XMLTool.appendChildElement(speech, SSML.E_MARK, SSML.namespaceURI);
			mark.setAttribute(SSML.A_NAME, id+":tm"+counter);
			Node text = doc.createTextNode(word);
			speech.appendChild(text);
			counter++;
		}
		Element mark = XMLTool.appendChildElement(speech, SSML.E_MARK);
		mark.setAttribute(SSML.A_NAME, id+":tm"+counter);
		
		fmlSender.sendXML(doc, meta.getTime());
		
		/* Send the speaking-state around */
		sendSpeaking();
		
		/* Set indices */
		emotionIndex = detectedEmotions.size();
		dActIndex = detectedDActs.size();
		
		/* Add the utterance to the history */
		utterance.setTime( meta.getTime() );
		utteranceHistory.add( utterance );
	}
	
	/**
	 * Sends around that the agent is speaking
	 * @throws JMSException
	 */
	public void sendSpeaking() throws JMSException
	{
		Map<String,String> dialogInfo = new HashMap<String,String>();		
		dialogInfo.put("speaker", "agent");
		dialogInfo.put("listener", "user");

		DialogStateInfo dsi = new DialogStateInfo(dialogInfo, null);
		dialogStateSender.sendStateInfo(dsi, meta.getTime());
	}
	
	/**
	 * Sends around that the agent is silent
	 * @throws JMSException
	 */
	public void sendListening() throws JMSException
	{
		Map<String,String> dialogInfo = new HashMap<String,String>();		
		dialogInfo.put("speaker", "user");
		dialogInfo.put("listener", "agent");

		DialogStateInfo dsi = new DialogStateInfo(dialogInfo, null);
		dialogStateSender.sendStateInfo(dsi, meta.getTime());
	}
	
	/**
	 * Sends around that the system has changed to a new character
	 * @param character the new character
	 * @throws JMSException
	 */
	public void sendNewCharacter( int character ) throws JMSException
	{
		Document semaineML = XMLTool.newDocument(SemaineML.E_CONTEXT, SemaineML.namespaceURI, SemaineML.version);
		Element rootNode = semaineML.getDocumentElement();

		Element characterElem = XMLTool.appendChildElement(rootNode, SemaineML.E_CHARACTER);
		characterElem.setAttribute(SemaineML.A_NAME, charNames.get(character));

		contextSender.sendXML(semaineML, meta.getTime());
	}
	
	/**
	 * Reads the received Message and tries to filter out the detected user speaking state.
	 * @param m - the received message
	 */
	public void setUserSpeakingState( SEMAINEUserStateMessage m )
	{
		UserStateInfo userInfo = m.getState();
		Map<String,String> userInfoMap = userInfo.getInfo();

		if( userInfoMap.get("behaviour").equals("speaking") ) {
			if( userSpeakingState != SPEAKING ) {
				userSpeakingState = SPEAKING;
				userSpeakingStateTime = meta.getTime();
			}
		} else if( userInfoMap.get("behaviour").equals("silence") ) {
			if( userSpeakingState != LISTENING ) {
				userSpeakingState = LISTENING;
				userSpeakingStateTime = meta.getTime();
			}
		}
	}
	
	/**
	 * Reads the received Message and tries to filter out the detected Emotion Events.
	 * @param m
	 */
	public void addDetectedEmotions( SEMAINEUserStateMessage m )
	{
		UserStateInfo userInfo = m.getState();
		Map<String,String> dialogInfoMap = userInfo.getInfo();
		
		if( dialogInfoMap.get("behaviour").equals("valence") ) {
			float intensity = Float.parseFloat( dialogInfoMap.get("behaviour intensity") );
			EmotionEvent ee = new EmotionEvent( meta.getTime(), 0, EmotionEvent.VALENCE, intensity );
			detectedEmotions.add( ee );
		} else if( dialogInfoMap.get("behaviour").equals("arousal") ) {
			float intensity = Float.parseFloat( dialogInfoMap.get("behaviour intensity") );
			EmotionEvent ee = new EmotionEvent( meta.getTime(), 0, EmotionEvent.AROUSAL, intensity );
			detectedEmotions.add( ee );
		} else if( dialogInfoMap.get("behaviour").equals("interest") ) {
			float intensity = Float.parseFloat( dialogInfoMap.get("behaviour intensity") );
			EmotionEvent ee = new EmotionEvent( meta.getTime(), 0, EmotionEvent.INTEREST, intensity );
			detectedEmotions.add( ee );
		}
	}
	
	/**
	 * Reads the received Message and tries to filter out the detected Dialogue Acts.
	 * @param m
	 * @throws JMSException
	 */
	public void addDetectedDActs( SEMAINEXMLMessage m ) throws JMSException
	{
		Element text = XMLTool.getChildElementByTagNameNS(m.getDocument().getDocumentElement(), SemaineML.E_TEXT, SemaineML.namespaceURI);
		if( text != null ) {
			String utterance = text.getTextContent();
			DialogueAct act = new DialogueAct(utterance);

			if( act != null ) {
				List<Element> features = XMLTool.getChildrenByTagNameNS(text, SemaineML.E_FEATURE, SemaineML.namespaceURI);
				for( Element feature : features ) {
					String f = feature.getAttribute( "name" );
					if( f.equals("positive") ) act.setPositive(true);
					if( f.equals("negative") ) act.setNegative(true);
					if( f.equals("agree") ) act.setAgree(true);
					if( f.equals("disagree") ) act.setDisagree(true);
					if( f.equals("about other people") ) act.setAboutOtherPeople(true);
					if( f.equals("about other character") ) act.setAboutOtherCharacter(true);
					if( f.equals("about current character") ) act.setAboutCurrentCharacter(true);
					if( f.equals("about own feelings") ) act.setAboutOwnFeelings(true);
					if( f.equals("pragmatic") ) act.setPragmatic(true);
					if( f.equals("about self") ) act.setTalkAboutSelf(true);
					if( f.equals("future") ) act.setFuture(true);
					if( f.equals("past") ) act.setPast(true);
					if( f.equals("event") ) act.setEvent(true);
					if( f.equals("action") ) act.setAction(true);
					if( f.equals("laugh") ) act.setLaugh(true);
					if( f.equals("change speaker") ) act.setChangeSpeaker(true);
					if( f.equals("target character") ) act.setTargetCharacter( feature.getAttribute("target") );
				}
				detectedDActs.add(act);
			}
		}
	}
	
	/**
	 * Makes a deepcopy of the given ArrayList
	 * @param utterances - the list to copy
	 * @return
	 */
	public ArrayList<String> utteranceCopy( ArrayList<String> utterances )
	{
		if( utterances == null ) {
			return new ArrayList<String>();
		}
		ArrayList<String> newUtterances = new ArrayList<String>();
		for( String str : utterances ) {
			newUtterances.add( ""+str );
		}
		return newUtterances;
	}
}
