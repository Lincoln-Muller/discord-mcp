package dev.saseq.services;

import dev.saseq.support.AttachmentSupport;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import net.dv8tion.jda.api.requests.restaction.CacheRestAction;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.utils.FileUpload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UserServiceAttachmentTest {

    private static final String FILE_PATH = "/outbox/test.zip";

    private enum FailureStage {
        SET_CONTENT,
        COMPLETE
    }

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
    void rejectsMissingUserIdForPrivateFileUpload() {
        JDA jda = mock(JDA.class);
        AttachmentSupport attachments = mock(AttachmentSupport.class);

        UserService service = new UserService(jda, attachments);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.sendPrivateFile("", FILE_PATH, null)
        );

        assertEquals("userId cannot be null", ex.getMessage());
        verifyNoInteractions(attachments);
    }

    @Test
    void rejectsUnknownUserForPrivateFileUpload() {
        JDA jda = mock(JDA.class);
        AttachmentSupport attachments = mock(AttachmentSupport.class);

        when(jda.getGuilds()).thenReturn(List.of());

        UserService service = new UserService(jda, attachments);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.sendPrivateFile("123", FILE_PATH, null)
        );

        assertEquals("User not found by userId", ex.getMessage());
        verifyNoInteractions(attachments);
    }

    @Test
    void sendsPrivateFileWithOptionalMessageToGuildMember() throws IOException {
        DmFixture fixture = new DmFixture();
        FileUpload upload = mock(FileUpload.class);
        MessageCreateAction action = mock(MessageCreateAction.class);
        Message sentMessage = mock(Message.class);

        when(fixture.attachments.createUpload(FILE_PATH)).thenReturn(upload);
        when(fixture.channel.sendFiles(upload)).thenReturn(action);
        when(action.setContent("release notes")).thenReturn(action);
        when(action.complete()).thenReturn(sentMessage);
        when(sentMessage.getJumpUrl()).thenReturn("https://discord.com/channels/@me/123/456");

        assertEquals(
                "Private file sent successfully. Message link: https://discord.com/channels/@me/123/456",
                fixture.service.sendPrivateFile("123", FILE_PATH, "release notes")
        );
        verify(fixture.attachments).createUpload(FILE_PATH);
        verify(fixture.channel).sendFiles(upload);
        verify(action).setContent("release notes");
        verify(upload, never()).close();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void omitsNullOrBlankMessageFromPrivateFileUpload(String message) {
        DmFixture fixture = new DmFixture();
        FileUpload upload = mock(FileUpload.class);
        MessageCreateAction action = mock(MessageCreateAction.class);
        Message sentMessage = mock(Message.class);

        when(fixture.attachments.createUpload(FILE_PATH)).thenReturn(upload);
        when(fixture.channel.sendFiles(upload)).thenReturn(action);
        when(action.complete()).thenReturn(sentMessage);
        when(sentMessage.getJumpUrl()).thenReturn("https://discord.com/channels/@me/123/456");

        fixture.service.sendPrivateFile("123", FILE_PATH, message);

        verify(action).complete();
        verify(action, never()).setContent(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void closesUploadAndPreservesFailureWhenPrivateChannelRejectsIt() {
        DmFixture fixture = new DmFixture();
        CloseTrackingInputStream input = new CloseTrackingInputStream();
        FileUpload upload = FileUpload.fromData(input, "test.zip");
        IllegalStateException rejection = new IllegalStateException("rejected");

        when(fixture.attachments.createUpload(FILE_PATH)).thenReturn(upload);
        when(fixture.channel.sendFiles(upload)).thenThrow(rejection);

        RuntimeException thrown = assertThrows(
                IllegalStateException.class,
                () -> fixture.service.sendPrivateFile("123", FILE_PATH, null)
        );

        assertSame(rejection, thrown);
        assertTrue(input.closed);
    }

    @ParameterizedTest
    @EnumSource(FailureStage.class)
    void closesUploadAndPreservesFailureBeforeJdaOwnership(FailureStage stage) {
        DmFixture fixture = new DmFixture();
        CloseTrackingInputStream input = new CloseTrackingInputStream();
        FileUpload upload = FileUpload.fromData(input, "test.zip");
        MessageCreateAction action = mock(MessageCreateAction.class);
        IllegalStateException rejection = new IllegalStateException(stage.name());

        when(fixture.attachments.createUpload(FILE_PATH)).thenReturn(upload);
        when(fixture.channel.sendFiles(upload)).thenReturn(action);
        if (stage == FailureStage.SET_CONTENT) {
            when(action.setContent("release notes")).thenThrow(rejection);
        } else {
            when(action.setContent("release notes")).thenReturn(action);
            when(action.complete()).thenThrow(rejection);
        }

        RuntimeException thrown = assertThrows(
                IllegalStateException.class,
                () -> fixture.service.sendPrivateFile("123", FILE_PATH, "release notes")
        );

        assertSame(rejection, thrown);
        assertTrue(input.closed);
    }

    @Test
    void leavesUploadOpenWhenJumpUrlFailsAfterJdaOwnership() {
        DmFixture fixture = new DmFixture();
        CloseTrackingInputStream input = new CloseTrackingInputStream();
        FileUpload upload = FileUpload.fromData(input, "test.zip");
        MessageCreateAction action = mock(MessageCreateAction.class);
        Message sentMessage = mock(Message.class);
        IllegalStateException failure = new IllegalStateException("jump URL unavailable");

        when(fixture.attachments.createUpload(FILE_PATH)).thenReturn(upload);
        when(fixture.channel.sendFiles(upload)).thenReturn(action);
        when(action.complete()).thenReturn(sentMessage);
        when(sentMessage.getJumpUrl()).thenThrow(failure);

        RuntimeException thrown = assertThrows(
                IllegalStateException.class,
                () -> fixture.service.sendPrivateFile("123", FILE_PATH, null)
        );

        assertSame(failure, thrown);
        assertFalse(input.closed);
    }

    private static final class DmFixture {
        private final JDA jda = mock(JDA.class);
        private final AttachmentSupport attachments = mock(AttachmentSupport.class);
        private final Guild guild = mock(Guild.class);
        private final Member member = mock(Member.class);
        private final User user = mock(User.class);
        private final PrivateChannel channel = mock(PrivateChannel.class);
        private final UserService service;

        @SuppressWarnings("unchecked")
        private DmFixture() {
            CacheRestAction<Member> retrieveMember = mock(CacheRestAction.class);
            CacheRestAction<PrivateChannel> openChannel = mock(CacheRestAction.class);

            when(jda.getGuilds()).thenReturn(List.of(guild));
            when(guild.retrieveMemberById("123")).thenReturn(retrieveMember);
            when(retrieveMember.complete()).thenReturn(member);
            when(member.getUser()).thenReturn(user);
            when(user.openPrivateChannel()).thenReturn(openChannel);
            when(openChannel.complete()).thenReturn(channel);

            service = new UserService(jda, attachments);
        }
    }
}
