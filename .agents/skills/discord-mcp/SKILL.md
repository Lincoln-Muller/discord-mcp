---
name: discord-mcp
description: Use when the user asks to DM someone, send or upload a file or ZIP to Discord, read recent Discord DMs, or deliver a finished artifact to a Discord contact later.
---

# Discord MCP

Use only the configured Discord MCP tools for Discord. Never use browser or computer-use when MCP is available. Never expose or request a bot token, use a normal user token/self-bot, or use unrelated moderation or server-management tools.

## Recipient

Never infer a recipient silently.

1. Use a numeric Discord user ID supplied by the user.
2. Otherwise check `LOCAL.md` beside this file for an exact alias. Never commit `LOCAL.md`.
3. Otherwise call `get_user_id_by_name` using the configured default guild.
4. Ask the user to choose if lookup is ambiguous. If lookup fails, ask for the numeric user ID.

Immediately before a real send, show and confirm the resolved recipient or channel, exact message, and user-visible outgoing filename. Wait for confirmation; drafts and hypothetical exercises are not authorization to send.

## Operations

- Send text: `send_private_message(userId, message)`.
- Read DMs: `read_private_messages(userId, count?, before?, after?, around?)`; use the smallest count that satisfies the request.
For any attachment, including a DM, channel upload, or delayed delivery:

1. Finish and verify the intended final artifact. For a ZIP, create it and validate its contents first.
2. Choose the final user-visible outgoing filename, copy only that artifact to the configured host Discord outbox, and verify the copy.
3. Resolve the recipient or channel.
4. Immediately before sending, confirm the resolved destination, exact message, and exact outgoing filename prepared in step 2.
5. For a DM call `send_private_file(userId, "/outbox/<filename>", message?)`. Only when a channel was explicitly requested, call `send_file(channelId, "/outbox/<filename>", message?)`. The MCP runtime path is `/outbox/...`, never the host source path.
6. Confirm the tool returned success and report both artifact and delivery results.

Do not send partial artifacts unless requested.

## Errors

- User not found: request the correct numeric ID or explain that a shared guild is required.
- Cannot send messages: explain that the recipient's DM/privacy settings may block the bot.
- File missing or outside `DISCORD_FILE_ROOT`: verify the host copy and use `/outbox/<filename>`; never weaken the root restriction.
- Upload too large: report Discord's returned error without guessing the limit.
