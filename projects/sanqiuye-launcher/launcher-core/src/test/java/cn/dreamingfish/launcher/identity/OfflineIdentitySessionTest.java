package cn.dreamingfish.launcher.identity;

import cn.dreamingfish.launcher.GameIdentity;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class OfflineIdentitySessionTest {
    @Test void namesAreTemporaryAndCaseSensitive() {
        var session=new OfflineIdentitySession();var alice=session.create("Alice");var lower=session.create("alice");
        assertEquals(UUID.nameUUIDFromBytes("OfflinePlayer:Alice".getBytes(StandardCharsets.UTF_8)),alice.id());
        assertNotEquals(alice.id(),lower.id());assertEquals(GameIdentity.Kind.OFFLINE,alice.kind());
        assertEquals(2,session.list().size());assertEquals(alice,session.create("Alice"));
        assertTrue(new OfflineIdentitySession().list().isEmpty());
        for(String name:new String[]{"ab","a".repeat(17),"三秋叶"," bad","bad-name"})assertThrows(IllegalArgumentException.class,()->session.create(name));
        for(int i=0;i<18;i++)session.create("User_"+i);
        assertThrows(IllegalArgumentException.class,()->session.create("User_20"));assertEquals(20,session.list().size());
    }
    @Test void switchingAndRemovalNeverMutateAnEarlierSnapshot() {
        var session=new OfflineIdentitySession();var first=session.create("First");var second=session.create("Second");
        assertEquals(first,session.current().orElseThrow());assertEquals(second,session.select(second.id()));
        assertEquals(second,session.current().orElseThrow());assertEquals(first,session.require(first.id()));
        session.remove(second.id());assertTrue(session.current().isEmpty());assertEquals("Second",second.name());
        assertThrows(IllegalArgumentException.class,()->session.require(second.id()));assertEquals(1,session.list().size());
    }
}
