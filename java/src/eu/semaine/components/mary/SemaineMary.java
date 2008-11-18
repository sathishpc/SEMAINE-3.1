/**
 * Copyright (C) 2008 DFKI GmbH. All rights reserved.
 * Use is subject to license terms -- see license.txt.
 */
package eu.semaine.components.mary;

import java.io.ByteArrayOutputStream;
import java.io.FileReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Locale;

import javax.jms.JMSException;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import marytts.Version;
import marytts.datatypes.MaryDataType;
import marytts.datatypes.MaryXML;
import marytts.datatypes.MaryDataType.Traits;
import marytts.modules.MaryModule;
import marytts.modules.ModuleRegistry;
import marytts.modules.Synthesis;
import marytts.modules.synthesis.Voice;
import marytts.server.EnvironmentChecks;
import marytts.server.Mary;
import marytts.server.MaryProperties;
import marytts.server.Request;

import java.lang.reflect.Method;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Category;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.WriterAppender;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.InputSource;

import eu.semaine.components.Component;
import eu.semaine.datatypes.xml.BML;
import eu.semaine.exceptions.MessageFormatException;
import eu.semaine.exceptions.SystemConfigurationException;
import eu.semaine.jms.message.SEMAINEMessage;
import eu.semaine.jms.message.SEMAINEXMLMessage;
import eu.semaine.jms.receiver.BMLReceiver;
import eu.semaine.jms.receiver.FMLReceiver;
import eu.semaine.jms.sender.BMLSender;
import eu.semaine.jms.sender.BytesSender;
import eu.semaine.jms.sender.FMLSender;
import eu.semaine.util.XMLTool;

/**
 * Speech preprocessor : To find pitch accent and boundaries
 * Speech BML realiser : Audio synthesis and phone timings
 * 
 * @author Sathish Chandra Pammi
 *
 */
public class SemaineMary extends Component 
{
	private FMLReceiver fmlReceiver;
	private BMLReceiver bmlReceiver;
	private BMLReceiver bmlPlanReceiver;
	private FMLSender fmlbmlSender;
	private BMLSender bmlSender;
	private BytesSender audioSender;
	
	private static TransformerFactory tFactory = null;
	private static Templates stylesheet = null;
    private Transformer transformer;
    
	/**
	 * @param componentName
	 * @throws JMSException
	 */
	public SemaineMary() throws JMSException 
	{
		super("SemaineMary");
		fmlReceiver = new FMLReceiver("semaine.data.action.selected.function");
		receivers.add(fmlReceiver); // to set up properly
		bmlReceiver = new BMLReceiver("semaine.data.action.selected.behaviour");
		receivers.add(bmlReceiver);
		bmlPlanReceiver = new BMLReceiver("semaine.data.synthesis.plan");
		receivers.add(bmlPlanReceiver);
		
		fmlbmlSender = new FMLSender("semaine.data.action.selected.speechpreprocessed", getName());
		senders.add(fmlbmlSender); // so it can be started etc
		bmlSender = new BMLSender("semaine.data.synthesis.plan.speechtimings", getName());
		senders.add(bmlSender);
		audioSender = new BytesSender("semaine.data.lowlevel.audio","AUDIO",getName());
		senders.add(audioSender); // so it can be started etc
	}
	
	protected void customStartIO() throws JMSException{
		
	    try {
        	long startTime = System.currentTimeMillis();
        	Mary.addJarsToClasspath();
            // Read properties:
            // (Will throw exceptions if problems are found)
			MaryProperties.readProperties();
			System.err.print("MARY server " + Version.specificationVersion() + " starting...");
			Mary.startup();
        	//startup();
            Runtime.getRuntime().addShutdownHook(new Thread() {
                public void run() {
                    Mary.shutdown();
                }
            });
            System.err.println(" started in " + (System.currentTimeMillis()-startTime)/1000. + " s");
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
  
	@Override
	public void react(SEMAINEMessage m) throws JMSException
	{
		if (!(m instanceof SEMAINEXMLMessage)) {
			throw new MessageFormatException("expected XML message, got "+m.getClass().getSimpleName());
		}
		
		if(m.getTopicName().equals("semaine.data.action.selected.function")){
			speechPreProcessor(m);
		}
		if(m.getTopicName().equals("semaine.data.synthesis.plan")){
			speechBMLRealiser(m);
		}
		if(m.getTopicName().equals("semaine.data.action.selected.behaviour")){
			SEMAINEXMLMessage xm = (SEMAINEXMLMessage)m;
			fmlbmlSender.sendXML(xm.getDocument(), xm.getUsertime(), xm.getEventType());
		}
		
	}
	
	
	// Speech Preprocessor
	private void speechPreProcessor(SEMAINEMessage m) throws JMSException{	
		
		SEMAINEXMLMessage xm = (SEMAINEXMLMessage)m;
		ByteArrayOutputStream ssmlos = new ByteArrayOutputStream();
		Request request = new Request(MaryDataType.SSML,MaryDataType.INTONATION,Locale.US,Voice.getDefaultVoice(Locale.ENGLISH),null,null,1,null);
		Document inputDoc = xm.getDocument();
		String inputText = xm.getText();
		
		try{	
			//DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			//factory.setNamespaceAware(true);
			//DocumentBuilder builder = factory.newDocumentBuilder();
			//inputDoc = builder.parse(new InputSource(new FileReader("dataformat1.xml")));
			//inputText = XMLTool.document2String(inputDoc);
			
			if (tFactory == null) {
	            tFactory = TransformerFactory.newInstance();
			 }
			if (stylesheet == null) {
		        StreamSource stylesheetStream =
		        new StreamSource( SemaineMary.class.getResourceAsStream(          
		                 "FML2SSML.xsl"));
		        stylesheet = tFactory.newTemplates(stylesheetStream);
		     }
			transformer = stylesheet.newTransformer();
			transformer.transform(new DOMSource(inputDoc), new StreamResult(ssmlos));
				
			Reader reader = new StringReader(ssmlos.toString());
			ByteArrayOutputStream  intonationOS = new ByteArrayOutputStream();
			request.readInputData(reader);
			request.process();
			request.writeOutputData(intonationOS);	
			String finalData = XMLTool.mergeTwoXMLFiles(inputText, intonationOS.toString(), SemaineMary.class.getResourceAsStream("FML-Intonation-Merge.xsl"), "semaine.mary.intonation");
			//System.out.println("PreProcessor: "+finalData);
			fmlbmlSender.sendTextMessage(finalData, xm.getUsertime(), xm.getEventType());
			
		} catch (TransformerConfigurationException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
		} catch (TransformerException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	private void speechBMLRealiser(SEMAINEMessage m) throws JMSException{
		
		SEMAINEXMLMessage xm = (SEMAINEXMLMessage)m;
		Document input = xm.getDocument();
		String inputText = xm.getText();
		ByteArrayOutputStream ssmlos = new ByteArrayOutputStream();

		try {
			
			//DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			//factory.setNamespaceAware(true);
			//DocumentBuilder builder = factory.newDocumentBuilder();
			//input = builder.parse(new InputSource(new FileReader("dataformat3.xml")));
			
			// Extracting SSML content from BML
			if (tFactory == null) {
            tFactory = TransformerFactory.newInstance();
			}
			if (stylesheet == null) {
	            StreamSource stylesheetStream =
	                new StreamSource(  SemaineMary.class.getResourceAsStream("BML2SSML.xsl"));     
	            stylesheet = tFactory.newTemplates(stylesheetStream);
	        }
			transformer = stylesheet.newTransformer();
			transformer.transform(new DOMSource(input), new StreamResult(ssmlos));
			//String inputText = XMLTool.document2String(input);
			
			// SSML to Realised Acoustics using MARY 
			Voice voice = Voice.getDefaultVoice(Locale.ENGLISH);
			AudioFormat af = voice.dbAudioFormat();
	        AudioFileFormat aff = new AudioFileFormat(AudioFileFormat.Type.WAVE,
	            af, AudioSystem.NOT_SPECIFIED);
			Request request = new Request(MaryDataType.get("SSML"),MaryDataType.get("REALISED_ACOUSTPARAMS"),Locale.US,voice,"","",1,aff);
			
			
			Reader reader = new StringReader(ssmlos.toString());
			ByteArrayOutputStream  realisedOS = new ByteArrayOutputStream();
			request.readInputData(reader);
			request.process();
			request.writeOutputData(realisedOS);
			//Merge realised acoustics into output format
			String finalData = XMLTool.mergeTwoXMLFiles(inputText, realisedOS.toString(), SemaineMary.class.getResourceAsStream("BML-RealisedSpeech-Merge.xsl"), "semaine.mary.realised.acoustics");
			bmlSender.sendTextMessage(finalData,  xm.getUsertime(), xm.getEventType());
			//System.out.println("BML Realiser: "+finalData);
			// SSML to AUDIO using MARY 
	        request = new Request(MaryDataType.SSML, MaryDataType.AUDIO, Locale.US, 
	            voice, "", "", 1, aff);
			reader = new StringReader(ssmlos.toString());
			ByteArrayOutputStream audioos = new ByteArrayOutputStream();
			request.readInputData(reader);
			request.process();
			request.writeOutputData(audioos);
			audioSender.sendBytesMessage(audioos.toByteArray(),  xm.getUsertime());
			
			
		} catch (TransformerConfigurationException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
		} catch (TransformerException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	
	}
	
	
}