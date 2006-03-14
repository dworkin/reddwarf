#pragma once

namespace SGS
{
	enum XMLElementKind
	{
		kStart,
		kEnd,
		kText
	};

	typedef std::map<std::wstring, std::wstring> AttributeMap;

	struct XMLElement
	{
		XMLElementKind kind;
		std::wstring name;
		AttributeMap attributes;
	};
}
