/*
 * AgentStateInfo.h
 *
 *  Created on: 27.07.2009
 *      Author: marc
 *  Copyright 2009 DFKI GmbH. All rights reserved.
 */

#ifndef SEMAINE_DATATYPES_STATEINFO_AGENTSTATEINFO_H
#define SEMAINE_DATATYPES_STATEINFO_AGENTSTATEINFO_H

#include <semaine/datatypes/stateinfo/StateInfo.h>
#include <semaine/datatypes/xml/SemaineML.h>

namespace semaine {

namespace datatypes {

namespace stateinfo {

class AgentStateInfo: public semaine::datatypes::stateinfo::StateInfo {
public:
	AgentStateInfo(XERCES_CPP_NAMESPACE::DOMDocument * doc)
	throw(semaine::cms::exceptions::MessageFormatException)
	: semaine::datatypes::stateinfo::StateInfo(doc, "AgentStateInfo",
			semaine::datatypes::xml::SemaineML::E_AGENTSTATE,
			semaine::datatypes::xml::SemaineML::namespaceURI,
			semaine::datatypes::stateinfo::StateInfo::AgentState)
	{}

	AgentStateInfo(std::map<std::string, std::string> const & infoItems)
	throw(semaine::cms::exceptions::SystemConfigurationException)
	: semaine::datatypes::stateinfo::StateInfo(infoItems, "AgentStateInfo",
			semaine::datatypes::stateinfo::StateInfo::AgentState)
	{}

	virtual ~AgentStateInfo() {}
};

}

}

}

#endif /* SEMAINE_DATATYPES_STATEINFO_AGENTSTATEINFO_H */
