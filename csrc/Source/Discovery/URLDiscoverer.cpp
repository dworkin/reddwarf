#include "stable.h"

#include "URLDiscoverer.h"

#include "XMLParser/LibXMLSAXParser.h"
#include "DiscoveryXMLHandler.h"

namespace
{
	//MSED - Need a function to download a document from an http URL
	std::vector<byte> URLDownloadFile(const std::wstring& url)
	{
		FILE* fp = _wfopen(url.c_str(), L"rb");
		fseek(fp, 0, SEEK_END);
		int size = ftell(fp);
		fseek(fp, 0, SEEK_SET);

		std::vector<byte> content;
		content.resize(size);
		fread(&content[0], size, 1, fp);
		fclose(fp);

		return content;
	}
}

URLDiscoverer::URLDiscoverer(const std::wstring& url) :
	mURL(url)
{
}

URLDiscoverer::~URLDiscoverer()
{
}

std::vector<IDiscoveredGamePtr> URLDiscoverer::GetGames()
{
	std::vector<byte> content = URLDownloadFile(mURL);

	std::auto_ptr<ISAXParser> parser(new LibXMLSAXParser());
	DiscoveryXMLHandler handler;
	parser->Parse(&content[0], content.size(), handler);
	return handler.GetGames();
}
