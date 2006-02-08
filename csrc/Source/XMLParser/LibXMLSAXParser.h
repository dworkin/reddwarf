#ifndef _LibXMLSAXParser_h
#define _LibXMLSAXParser_h

#include "ISAXParser.h"

class LibXMLSAXParser : public ISAXParser
{
public:
	virtual void Parse(const byte* content, size_t size, ISAXHandler& handler);

private:
	static void OnStartElement(void* ctx, const unsigned char* name, const unsigned char** atts);
	static void OnEndElement(void* ctx, const unsigned char* name);
};

#endif
