#ifndef _ISAXHandler_h
#define _ISAXHandler_h

typedef std::map<std::wstring, std::wstring> AttributeMap;

class ISAXHandler
{
public:
	virtual void OnStartElement(const std::wstring& name, /*MSED-const */AttributeMap& attributes) = 0;
	virtual void OnEndElement(const std::wstring& name) = 0;
};

#endif
