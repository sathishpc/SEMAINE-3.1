/**
 * Copyright (C) 2008 DFKI GmbH. All rights reserved.
 * Use is subject to license terms -- see license.txt.
 */
package eu.semaine.jms.receiver;

import javax.jms.JMSException;
import javax.jms.Message;

import eu.semaine.exceptions.MessageFormatException;
import eu.semaine.jms.message.SEMAINEFeatureMessage;
import eu.semaine.jms.message.SEMAINEMessage;

/**
 * An abstraction of the Receiver for feature vectors.
 * @author marc
 *
 */
public class FeatureReceiver extends Receiver
{
	private String[] featureNames;

	/**
	 * Create a receiver that will listen for all messages in the given Topic
	 * on the default JMS server.
	 * The default JMS server is determined as follows:
	 * <ul>
	 * <li>jms URL: read from system property <code>jms.url</code>, default value <code>tcp://localhost:61616</code>;</li>
	 * <li>jms User: read from system property <code>jms.user</code>, default value <code>null</code>;</li>
	 * <li>jms Password: read from system property <code>jms.password</code>, default value <code>null</code>.</li>
	 * </ul>
	 * @param topic the name of the JMS Topic to listen to.
	 */
	public FeatureReceiver(String topicName) throws JMSException
	{
		super(topicName);
	}
	
	/**
	 * Create a receiver that will listen only to the messages in the given Topic
	 * that will pass the given messageSelector,
	 * on the default JMS server.
	 * The default JMS server is determined as follows:
	 * <ul>
	 * <li>jms URL: read from system property <code>jms.url</code>, default value <code>tcp://localhost:61616</code>;</li>
	 * <li>jms User: read from system property <code>jms.user</code>, default value <code>null</code>;</li>
	 * <li>jms Password: read from system property <code>jms.password</code>, default value <code>null</code>.</li>
	 * </ul>
	 * @param topicName the name of the JMS Topic to listen to.
	 * @param messageSelector a message selector expression, see e.g. http://java.sun.com/javaee/5/docs/api/javax/jms/Message.html
	 * for the detailed description.
	 */
	public FeatureReceiver(String topicName, String messageSelector)
	throws JMSException
	{
		super(topicName, messageSelector);
	}

	/**
	 * Create a receiver that will listen for all messages in the given Topic
	 * on the given JMS server.
	 * @param jmsUrl the url where to contact the JMS server
	 * @param jmsUser the username to use (can be null)
	 * @param jmsPassword the password to use (can be null)
	 * @param topic the name of the JMS Topic to listen to.
	 */
	public FeatureReceiver(String jmsUrl, String jmsUser, String jmsPassword, String topicName) throws JMSException
	{
		super(jmsUrl, jmsUser, jmsPassword, topicName);
	}
	
	/**
	 * Create a receiver that will listen only to the messages in the given Topic
	 * that will pass the given messageSelector,
	 * on the given JMS server.
	 * @param jmsUrl the url where to contact the JMS server
	 * @param jmsUser the username to use (can be null)
	 * @param jmsPassword the password to use (can be null)
	 * @param topicName the name of the JMS Topic to listen to.
	 * @param messageSelector a message selector expression, see e.g. http://java.sun.com/javaee/5/docs/api/javax/jms/Message.html
	 * for the detailed description.
	 */
	public FeatureReceiver(String jmsUrl, String jmsUser, String jmsPassword, String topicName, String messageSelector)
	throws JMSException
	{
		super(jmsUrl, jmsUser, jmsPassword, topicName, messageSelector);
	}
	
	protected SEMAINEMessage createSEMAINEMessage(Message message)
	throws MessageFormatException
	{
		SEMAINEFeatureMessage fm = new SEMAINEFeatureMessage(message);
		try {
			if (featureNames == null && fm.getFeatureNames() != null) {
				featureNames = fm.getFeatureNames();
			} else if (featureNames != null && fm.getFeatureNames() == null) {
				fm.setFeatureNames(featureNames);
			}
		} catch (JMSException e) {
			throw new MessageFormatException("Problem accessing feature names", e);
		}
		return fm;
	}

}
