/**
 * Copyright (C) 2008 DFKI GmbH. All rights reserved.
 * Use is subject to license terms -- see license.txt.
 */
package eu.semaine.jms.receiver;

import javax.jms.JMSException;
import javax.jms.Message;

import eu.semaine.exceptions.MessageFormatException;
import eu.semaine.jms.message.SEMAINEBytesMessage;
import eu.semaine.jms.message.SEMAINEMessage;

/**
 * Basic XML parsing functionality.
 * @author marc
 *
 */
public class BytesReceiver extends Receiver
{

	/**
	 * Create a receiver that will listen for all messages in the given Topic.
	 * @param topic the name of the JMS Topic to listen to.
	 */
	public BytesReceiver(String topicName) throws JMSException
	{
		super(topicName);
	}

	/**
	 * Create a receiver that will listen only to the messages in the given Topic
	 * that will pass the given messageSelector. 
	 * @param topicName the name of the JMS Topic to listen to.
	 * @param messageSelector a message selector expression, see e.g. http://java.sun.com/javaee/5/docs/api/javax/jms/Message.html
	 * for the detailed description.
	 */
	public BytesReceiver(String topicName, String messageSelector)
	throws JMSException
	{
		super(topicName, messageSelector);
	}

	@Override
	protected SEMAINEMessage createSEMAINEMessage(Message message)
	throws MessageFormatException
	{
		return new SEMAINEBytesMessage(message);
	}

}
