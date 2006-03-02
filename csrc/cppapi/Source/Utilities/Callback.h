#ifndef _Callback_h
#define _Callback_h

namespace Darkstar
{
	class CLIENTAPI ICallback
	{
	public:
		virtual ~ICallback();
	};

	class CLIENTAPI NameCallback : public ICallback
	{
	public:
		const std::wstring& GetPrompt() const;
		void SetPrompt(const std::wstring& prompt);
		const std::wstring& GetDefaultName() const;
		void SetDefaultName(const std::wstring& defaultName);
		const std::wstring& GetName() const;
		void SetName(const std::wstring& name);

	private:
		std::wstring mPrompt;
		std::wstring mDefaultName;
		std::wstring mName;
	};

	class CLIENTAPI PasswordCallback : public ICallback
	{
	public:
		const std::wstring& GetPrompt() const;
		void SetPrompt(const std::wstring& prompt);
		bool IsEchoOn() const;
		void SetIsEchoOn(bool isEchoOn);
		const std::wstring& GetPassword() const;
		void SetPassword(const std::wstring& password);

	private:
		std::wstring mPrompt;
		bool mIsEchoOn;
		std::wstring mPassword;
	};

	class CLIENTAPI TextInputCallback : public ICallback
	{
	public:
		const std::wstring& GetPrompt() const;
		void SetPrompt(const std::wstring& prompt);
		const std::wstring& GetDefaultText() const;
		void SetDefaultText(const std::wstring& defaultText);
		const std::wstring& GetText() const;
		void SetText(const std::wstring& text);

	private:
		std::wstring mPrompt;
		std::wstring mDefaultText;
		std::wstring mText;
	};
}

#endif
