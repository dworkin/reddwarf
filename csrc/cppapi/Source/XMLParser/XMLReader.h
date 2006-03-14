#pragma once

#include "XMLElement.h"

struct XML_ParserStruct;
typedef wchar_t XML_Char;

namespace SGS
{
	struct IStream;
	
	namespace Internal
	{
		class XMLAdapter;
	}

	class XMLReader
	{
	public:
		XMLReader(IStream* pStream);
		~XMLReader();
	
		bool IsEOF();
		const XMLElement& ReadElement();

	private:
		void Parse();

		IStream* mpStream;
		XML_ParserStruct* mParser;
		XMLElement* mpCurrentElement;
		std::list<XMLElement*> mElements;

		friend class SGS::Internal::XMLAdapter;
	};
}
