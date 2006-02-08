#include "stable.h"

#include "LibXMLSAXParser.h"

#include "ISAXHandler.h"

#include "Platform/Platform.h"

#include "libxml/SAX2.h"

namespace
{
	// Part of non-parsing hack
	AttributeMap toAttributeMap(std::pair<std::wstring, std::wstring>* pairs, int count)
	{
		AttributeMap attributes;
		for (int i = 0; i < count; ++i)
			attributes[pairs[i].first] = pairs[i].second;
		return attributes;
	}
}

void LibXMLSAXParser::Parse(const byte* content, size_t size, ISAXHandler& handler)
{
	xmlSAXHandler SAXHandler =
	{
		NULL, //internalSubsetSAXFunc internalSubset;
		NULL, //isStandaloneSAXFunc isStandalone;
		NULL, //hasInternalSubsetSAXFunc hasInternalSubset;
		NULL, //hasExternalSubsetSAXFunc hasExternalSubset;
		NULL, //resolveEntitySAXFunc resolveEntity;
		NULL, //getEntitySAXFunc getEntity;
		NULL, //entityDeclSAXFunc entityDecl;
		NULL, //notationDeclSAXFunc notationDecl;
		NULL, //attributeDeclSAXFunc attributeDecl;
		NULL, //elementDeclSAXFunc elementDecl;
		NULL, //unparsedEntityDeclSAXFunc unparsedEntityDecl;
		NULL, //setDocumentLocatorSAXFunc setDocumentLocator;
		NULL, //startDocumentSAXFunc startDocument;
		NULL, //endDocumentSAXFunc endDocument;
		OnStartElement, //startElementSAXFunc startElement;
		OnEndElement, //endElementSAXFunc endElement;
		NULL, //referenceSAXFunc reference;
		NULL, //charactersSAXFunc characters;
		NULL, //ignorableWhitespaceSAXFunc ignorableWhitespace;
		NULL, //processingInstructionSAXFunc processingInstruction;
		NULL, //commentSAXFunc comment;
		NULL, //warningSAXFunc warning;
		NULL, //errorSAXFunc error;
		NULL, //fatalErrorSAXFunc fatalError; /* unused error() get all the errors */
		NULL, //getParameterEntitySAXFunc getParameterEntity;
		NULL, //cdataBlockSAXFunc cdataBlock;
		NULL, //externalSubsetSAXFunc externalSubset;
		0, //unsigned int initialized;
		/* The following fields are extensions available only on version 2 */
		NULL, //void *_private;
		NULL, //startElementNsSAX2Func startElementNs;
		NULL, //endElementNsSAX2Func endElementNs;
		NULL, //xmlStructuredErrorFunc serror;
	};

	int result = xmlSAXUserParseMemory(&SAXHandler, reinterpret_cast<void*>(&handler), reinterpret_cast<const char*>(content), static_cast<int>(size));
	if (result != 0)
		(void)0; // MSED - SAX Parsing Error
}

void LibXMLSAXParser::OnStartElement(void* ctx, const xmlChar* name, const xmlChar** atts)
{
	ISAXHandler* pHandler = reinterpret_cast<ISAXHandler*>(ctx);

	AttributeMap attributes;
	if (atts != NULL)
		for ( ; atts[0] != NULL; atts += 2)
			attributes[Platform::ToUnicode((const char*)atts[0])] = Platform::ToUnicode((const char*)atts[1]);

	pHandler->OnStartElement(Platform::ToUnicode((const char*)name), attributes);
}

void LibXMLSAXParser::OnEndElement(void* ctx, const xmlChar* name)
{
	ISAXHandler* pHandler = reinterpret_cast<ISAXHandler*>(ctx);
	pHandler->OnEndElement(Platform::ToUnicode((const char*)name));
}
