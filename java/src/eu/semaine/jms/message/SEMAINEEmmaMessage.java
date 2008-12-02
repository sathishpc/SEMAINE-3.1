/**
 * Copyright (C) 2008 DFKI GmbH. All rights reserved.
 * Use is subject to license terms -- see license.txt.
 */
package eu.semaine.jms.message;

import java.util.List;

import javax.jms.Message;

import org.w3c.dom.Element;

import eu.semaine.datatypes.xml.EMMA;
import eu.semaine.datatypes.xml.EmotionML;
import eu.semaine.datatypes.xml.SemaineML;
import eu.semaine.exceptions.MessageFormatException;
import eu.semaine.util.XMLTool;

/**
 * @author marc
 *
 */
public class SEMAINEEmmaMessage extends SEMAINEXMLMessage {

	/**
	 * @param message
	 * @throws MessageFormatException
	 */
	public SEMAINEEmmaMessage(Message message) throws MessageFormatException
	{
		super(message);
	}

	public Element getOneOf()
	{
		return XMLTool.getChildElementByTagNameNS(doc.getDocumentElement(), EMMA.E_ONEOF, EMMA.namespace);
	}
	
	public Element getTopLevelInterpretation()
	{
		return XMLTool.getChildElementByTagNameNS(doc.getDocumentElement(), EMMA.E_INTERPRETATION, EMMA.namespace);
	}


	public List<Element> getEmotionElements(Element interpretation)
	{
		return XMLTool.getChildrenByTagNameNS(interpretation, EmotionML.E_EMOTION, EmotionML.namespace);
	}

	public List<Element> getFeatureElements(Element interpretation)
	{
		return XMLTool.getChildrenByTagNameNS(interpretation, SemaineML.E_FEATURE, SemaineML.namespace);
	}

	public List<Element> getBehaviourElements(Element interpretation)
	{
		return XMLTool.getChildrenByTagNameNS(interpretation, SemaineML.E_BEHAVIOUR, SemaineML.namespace);
	}
	
	public List<Element> getTextElements(Element interpretation)
	{
		return XMLTool.getChildrenByTagNameNS(interpretation, SemaineML.E_TEXT, SemaineML.namespace);
	}

}
