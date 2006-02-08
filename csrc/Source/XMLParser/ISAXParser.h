#ifndef _ISAXParser_h
#define _ISAXParser_h

class ISAXHandler;

class ISAXParser
{
public:
	virtual void Parse(const byte* content, size_t size, ISAXHandler& handler) = 0;
};

#endif
