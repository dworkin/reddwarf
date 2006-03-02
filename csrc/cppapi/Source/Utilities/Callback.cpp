#include "stable.h"

#include "Callback.h"

using namespace Darkstar;

ICallback::~ICallback()
{
}

const std::wstring& NameCallback::GetPrompt() const
{
	return mPrompt;
}

void NameCallback::SetPrompt(const std::wstring& prompt)
{
	mPrompt = prompt;
}

const std::wstring& NameCallback::GetDefaultName() const
{
	return mDefaultName;
}

void NameCallback::SetDefaultName(const std::wstring& defaultName)
{
	mDefaultName = defaultName;
}

const std::wstring& NameCallback::GetName() const
{
	return mName;
}

void NameCallback::SetName(const std::wstring& name)
{
	mName = name;
}

const std::wstring& PasswordCallback::GetPrompt() const
{
	return mPrompt;
}

void PasswordCallback::SetPrompt(const std::wstring& prompt)
{
	mPrompt = prompt;
}

bool PasswordCallback::IsEchoOn() const
{
	return mIsEchoOn;
}

void PasswordCallback::SetIsEchoOn(bool isEchoOn)
{
	mIsEchoOn = isEchoOn;
}

const std::wstring& PasswordCallback::GetPassword() const
{
	return mPassword;
}

void PasswordCallback::SetPassword(const std::wstring& password)
{
	mPassword = password;
}

const std::wstring& TextInputCallback::GetPrompt() const
{
	return mPrompt;
}

void TextInputCallback::SetPrompt(const std::wstring& prompt)
{
	mPrompt = prompt;
}

const std::wstring& TextInputCallback::GetDefaultText() const
{
	return mDefaultText;
}

void TextInputCallback::SetDefaultText(const std::wstring& defaultText)
{
	mDefaultText = defaultText;
}

const std::wstring& TextInputCallback::GetText() const
{
	return mText;
}

void TextInputCallback::SetText(const std::wstring& text)
{
	mText = text;
}
