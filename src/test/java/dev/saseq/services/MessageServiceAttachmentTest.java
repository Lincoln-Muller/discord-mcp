package dev.saseq.services;

import dev.saseq.support.AttachmentSupport;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.utils.FileUpload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MessageServiceAttachmentTest {

    private static final class CloseTrackingInputStream extends ByteArrayInputStream {
        private boolean closed;

        private CloseTrackingInputStream() {
            super(new byte[]{1});
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    @Test
    void sendsFileWithOptionalMessageToKnownChannel() throws IOException {
        JDA jda = mock(JDA.class);
        AttachmentSupport attachments = mock(AttachmentSupport.class);
        TextChannel channel = mock(TextChannel.class);
        FileUpload upload = mock(FileUpload.class);
        MessageCreateAction action = mock(MessageCreateAction.class);
        Message sentMessage = mock(Message.class);

        when(jda.getTextChannelById("123")).thenReturn(channel);
        when(attachments.createUpload("/outbox/test.zip")).thenReturn(upload);
        when(channel.sendFiles(upload)).thenReturn(action);
        when(action.setContent("release notes")).thenReturn(action);
        when(action.complete()).thenReturn(sentMessage);
        when(sentMessage.getJumpUrl()).thenReturn("https://discord.com/channels/1/123/456");

        MessageService service = new MessageService(jda, attachments);

        assertEquals(
                "File sent successfully. Message link: https://discord.com/channels/1/123/456",
                service.sendFile("123", "/outbox/test.zip", "release notes")
        );
        verify(attachments).createUpload("/outbox/test.zip");
        verify(channel).sendFiles(upload);
        verify(action).setContent("release notes");
        verify(upload, never()).close();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void omitsNullOrBlankMessageFromFileUpload(String message) {
        JDA jda = mock(JDA.class);
        AttachmentSupport attachments = mock(AttachmentSupport.class);
        TextChannel channel = mock(TextChannel.class);
        FileUpload upload = mock(FileUpload.class);
        MessageCreateAction action = mock(MessageCreateAction.class);
        Message sentMessage = mock(Message.class);

        when(jda.getTextChannelById("123")).thenReturn(channel);
        when(attachments.createUpload("/outbox/test.zip")).thenReturn(upload);
        when(channel.sendFiles(upload)).thenReturn(action);
        when(action.complete()).thenReturn(sentMessage);
        when(sentMessage.getJumpUrl()).thenReturn("https://discord.com/channels/1/123/456");

        MessageService service = new MessageService(jda, attachments);

        service.sendFile("123", "/outbox/test.zip", message);

        verify(action).complete();
        verify(action, never()).setContent(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void rejectsUnknownChannelForFileUpload() {
        JDA jda = mock(JDA.class);
        AttachmentSupport attachments = mock(AttachmentSupport.class);

        when(jda.getTextChannelById("123")).thenReturn(null);
        when(jda.getNewsChannelById("123")).thenReturn(null);
        when(jda.getThreadChannelById("123")).thenReturn(null);

        MessageService service = new MessageService(jda, attachments);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.sendFile("123", "/outbox/test.zip", null)
        );

        assertEquals("Channel not found by channelId", ex.getMessage());
        verifyNoInteractions(attachments);
    }

    @Test
    void closesUploadWhenChannelRejectsItBeforeTakingOwnership() {
        JDA jda = mock(JDA.class);
        AttachmentSupport attachments = mock(AttachmentSupport.class);
        TextChannel channel = mock(TextChannel.class);
        CloseTrackingInputStream input = new CloseTrackingInputStream();
        FileUpload upload = FileUpload.fromData(input, "test.zip");

        when(jda.getTextChannelById("123")).thenReturn(channel);
        when(attachments.createUpload("/outbox/test.zip")).thenReturn(upload);
        when(channel.sendFiles(upload)).thenThrow(new IllegalStateException("rejected"));

        MessageService service = new MessageService(jda, attachments);

        assertThrows(
                IllegalStateException.class,
                () -> service.sendFile("123", "/outbox/test.zip", null)
        );
        assertTrue(input.closed);
    }
}
