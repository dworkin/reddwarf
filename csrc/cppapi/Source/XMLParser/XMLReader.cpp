#include "stable.h"

#include "XMLReader.h"

#include "Platform/IStream.h"

#include "Expat-1.95.8/Source/lib/expat_config.h"
#include "Expat-1.95.8/Source/lib/expat.h"

namespace Darkstar
{
	namespace Internal
	{
		class XMLAdapter
		{
		public:
			static void XMLCALL OnStartElement(void *userData, const XML_Char *name, const XML_Char **atts);
			static void XMLCALL OnEndElement(void *userData, const XML_Char *name);
			static void XMLCALL OnCharacterData(void *userData, const XML_Char *s, int len);
		};
	}
}

Darkstar::XMLReader::XMLReader(Darkstar::IStream* pStream) :
	mpStream(pStream),
	mParser(XML_ParserCreate(NULL)),
	mpCurrentElement(NULL)
{
	if (!mParser)
		return;

	XML_SetUserData(mParser, reinterpret_cast<void*>(this));
	XML_SetElementHandler(mParser, &Internal::XMLAdapter::OnStartElement, &Internal::XMLAdapter::OnEndElement);
	XML_SetCharacterDataHandler(mParser, &Internal::XMLAdapter::OnCharacterData);
}

Darkstar::XMLReader::~XMLReader()
{
	if (mParser != NULL)
		XML_ParserFree(mParser);

	delete mpCurrentElement;
	for (std::list<Darkstar::XMLElement*>::iterator iter = mElements.begin(); iter != mElements.end(); ++iter)
		delete *iter;
}

bool Darkstar::XMLReader::IsEOF()
{
	if (!mParser || !mpStream)
		return true;
	else
	{
		Parse();
		return mElements.empty();
	}
}

const Darkstar::XMLElement& Darkstar::XMLReader::ReadElement()
{
	//ASSERT(mParser);
	//ASSERT(mpStream);

	Parse();
	//ASSERT(!mElements.empty());

	delete mpCurrentElement;
	mpCurrentElement = mElements.front();
	mElements.pop_front();
	return *mpCurrentElement;
}

class xml_error : public std::exception
{
public:
	xml_error(const wchar_t* /*what*/)
	{
	}
};

#define throwXmlError(xmlFile, xmlLine, what)	throw xml_error(__FILE__, __LINE__, xmlFile, xmlLine, what)

void Darkstar::XMLReader::Parse()
{
	while (mElements.empty() && !mpStream->IsEOF())
	{
		void* data;
		size_t length;

		char buf[BUFSIZ];
		if (mpStream->CanReadOptimized())
		{
			length = mpStream->ReadOptimized(data, mpStream->GetLength());
		}
		else
		{
			data = buf;
			length = mpStream->Read(buf, sizeof(buf));
		}

		bool done = mpStream->IsEOF();
		if (XML_Parse(mParser, (char*)data, (int)length, done) == XML_STATUS_ERROR) 
		{
			//int line = XML_GetCurrentLineNumber(mParser); 
			throw xml_error(XML_ErrorString(XML_GetErrorCode(mParser)));
		}
	}
}

void XMLCALL Darkstar::Internal::XMLAdapter::OnStartElement(void *userData, const XML_Char *name, const XML_Char **atts)
{
	Darkstar::XMLElement* pElement = new Darkstar::XMLElement; 
	pElement->kind = Darkstar::kStart; 
	pElement->name = name; 
	while ( *atts != NULL )
	{
		const XML_Char* key		= *atts++; 
		const XML_Char* value	= *atts++; 
		pElement->attributes[key] = value; 
	}

	Darkstar::XMLReader* pThis = reinterpret_cast<Darkstar::XMLReader*>(userData);
	pThis->mElements.push_back(pElement); 
}

void XMLCALL Darkstar::Internal::XMLAdapter::OnEndElement(void *userData, const XML_Char *name)
{
	Darkstar::XMLElement* pElement = new Darkstar::XMLElement; 
	pElement->kind = Darkstar::kEnd; 
	pElement->name = name; 

	Darkstar::XMLReader* pThis = reinterpret_cast<Darkstar::XMLReader*>(userData);
	pThis->mElements.push_back(pElement); 
}

void XMLCALL Darkstar::Internal::XMLAdapter::OnCharacterData(void *userData, const XML_Char *s, int len)
{
	Darkstar::XMLElement* pElement = new Darkstar::XMLElement; 
	pElement->kind = Darkstar::kText; 
	pElement->name = std::wstring( s, len ); 

	Darkstar::XMLReader* pThis = reinterpret_cast<Darkstar::XMLReader*>(userData);
	pThis->mElements.push_back(pElement); 
}
