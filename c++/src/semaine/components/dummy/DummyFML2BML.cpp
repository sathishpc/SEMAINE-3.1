/*
 *  DummyFML2BML.cpp
 *  semaine
 *
 *  Created by Marc Schröder on 19.09.08.
 *  Copyright 2008 DFKI GmbH. All rights reserved.
 *
 */

#include "DummyFML2BML.h"

#include <semaine/cms/message/SEMAINEXMLMessage.h>
#include <semaine/cms/exceptions/MessageFormatException.h>
#include <semaine/util/XMLTool.h>
#include <semaine/datatypes/xml/BML.h>

#include <semaine/datatypes/stateinfo/StateInfo.h>
#include <semaine/datatypes/stateinfo/AgentStateInfo.h>
#include <semaine/cms/message/SEMAINEStateMessage.h>

#include <cstdlib>
#include <sstream>
#include <decaf/lang/System.h>

using namespace semaine::util;
using namespace semaine::datatypes::xml;
using namespace XERCES_CPP_NAMESPACE;
using namespace semaine::datatypes::stateinfo;

namespace semaine {
namespace components {
namespace dummy {

DummyFML2BML::DummyFML2BML() throw(CMSException) :
	Component("DummyFML2BML")
{
	fmlReceiver = new FMLReceiver("semaine.data.action.selected.function");
	receivers.push_back(fmlReceiver);  // to set up properly
	bmlReceiver = new BMLReceiver("semaine.data.action.selected.behaviour");
	receivers.push_back(bmlReceiver);  // to set up properly
	bmlSender = new BMLSender("semaine.data.synthesis.plan", getName());
	senders.push_back(bmlSender);
	stateReceiver = new StateReceiver("semaine.data.state.user.behaviour", StateInfo::UserState);
	receivers.push_back(stateReceiver);
	stateSender = new StateSender("semaine.data.state.agent", StateInfo::AgentState, getName());
	senders.push_back(stateSender);
}

DummyFML2BML::~DummyFML2BML()
{
	delete fmlReceiver;
	delete bmlReceiver;
	delete bmlSender;
}


void DummyFML2BML::react(SEMAINEMessage * m) throw(CMSException)
{
	SEMAINEStateMessage * sm = dynamic_cast<SEMAINEStateMessage *>(m);
	if (sm != NULL) {
		StateInfo * info = sm->getState();
		if (info->hasInfo("arousal")) {
			std::map<std::string, std::string> agentInfo;
			agentInfo["happiness"] = "0.8";
			AgentStateInfo agentState(agentInfo);
			stateSender->sendStateInfo(&agentState, meta.getTime());
		}
		return;
	}

	SEMAINEXMLMessage * xm = dynamic_cast<SEMAINEXMLMessage *>(m);
	if (xm == NULL) {
		throw semaine::cms::exceptions::MessageFormatException("expected XML message, gut "+std::string(typeid(*m).name()));
	}
	bool isFML = xm->getDatatype() == "FML";
	if (isFML) {
		// extract the BML and send that along
		DOMElement * bml = XMLTool::needChildElementByTagNameNS(xm->getDocument()->getDocumentElement(), BML::E_BML, BML::namespaceURI);
		DOMDocument * doc = XMLTool::newDocument(BML::E_BML, BML::namespaceURI);
		//doc->adoptNode(bml);
		DOMElement * myBml = (DOMElement *) doc->importNode(bml, true /* deep */);
		doc->replaceChild(myBml, doc->getDocumentElement());
		bmlSender->sendXML(doc, xm->getUsertime(), xm->getEventType());
		delete doc;
	} else { // BML -- send as is
		bmlSender->sendXML(xm->getDocument(), xm->getUsertime(), xm->getEventType());
	}
}


} // namespace dummy
} // namespace components
} // namespace semaine
