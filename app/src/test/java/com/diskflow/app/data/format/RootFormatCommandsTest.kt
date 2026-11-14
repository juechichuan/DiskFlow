package com.diskflow.app.data.format

import com.diskflow.app.domain.model.FileSystemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RootFormatCommandsTest {

    @Test
    fun `label is stripped of shell metacharacters`() {
        assertEquals("DISKFLOW", RootFormatCommands.sanitizeLabel("  "))
        assertEquals("MyDisk", RootFormatCommands.sanitizeLabel("My Disk"))
        assertEquals("abc123", RootFormatCommands.sanitizeLabel("a;b&c1'2\"3"))
        assertEquals("ABCDEFGHIJK", RootFormatCommands.sanitizeLabel("ABCDEFGHIJKLMNOP"))
    }

    @Test
    fun `volume id resolves only public vold volumes`() {
        val output = """
            private mounted null
            public:179,65 mounted 1A2B-3C4D
            emulated mounted null
        """.trimIndent()
        assertEquals("public:179,65", RootFormatCommands.volumeIdForUuid(output, "1A2B-3C4D"))
        assertEquals("public:179,65", RootFormatCommands.volumeIdForUuid(output, "1a2b-3c4d"))
        assertNull(RootFormatCommands.volumeIdForUuid(output, "FFFF-0000"))
        assertEquals("/dev/block/vold/public:179,65", RootFormatCommands.blockDevicePath("public:179,65"))
        assertNull(RootFormatCommands.blockDevicePath("emulated"))
    }

    @Test
    fun `commands prefer vfat then fall back to newfs_msdos`() {
        val vfat = RootFormatCommands.toolCommand(
            FileSystemType.FAT32,
            "DISK",
            "/dev/block/vold/public:179,65",
            setOf("mkfs.vfat", "newfs_msdos"),
        )
        assertEquals("mkfs.vfat -F 32 -n DISK /dev/block/vold/public:179,65", vfat)

        val fallback = RootFormatCommands.toolCommand(
            FileSystemType.FAT32,
            "DISK",
            "/dev/block/vold/public:179,65",
            setOf("newfs_msdos"),
        )
        assertEquals("newfs_msdos -F 32 -L DISK /dev/block/vold/public:179,65", fallback)
    }

    @Test
    fun `missing tools yield no command`() {
        assertNull(
            RootFormatCommands.toolCommand(
                FileSystemType.NTFS,
                "DISK",
                "/dev/block/vold/public:179,65",
                setOf("mkfs.vfat"),
            ),
        )
        assertNull(
            RootFormatCommands.toolCommand(
                FileSystemType.EXFAT,
                "DISK",
                "/dev/block/vold/public:179,65",
                emptySet(),
            ),
        )
    }

    @Test
    fun `exfat ntfs and ext4 command shapes`() {
        val device = "/dev/block/vold/public:179,65"
        assertEquals(
            "mkfs.exfat -n DISK $device",
            RootFormatCommands.toolCommand(FileSystemType.EXFAT, "DISK", device, setOf("mkfs.exfat")),
        )
        assertEquals(
            "mkfs.ntfs -f -L DISK $device",
            RootFormatCommands.toolCommand(FileSystemType.NTFS, "DISK", device, setOf("mkfs.ntfs")),
        )
        assertEquals(
            "mke2fs -t ext4 -F -L DISK $device",
            RootFormatCommands.toolCommand(FileSystemType.EXT4, "DISK", device, setOf("mke2fs")),
        )
    }

    @Test
    fun `labels are clamped before reaching the shell`() {
        val command = RootFormatCommands.toolCommand(
            FileSystemType.EXFAT,
            "A/B;C\"D",
            "/dev/block/vold/public:179,65",
            setOf("mkfs.exfat"),
        )
        assertTrue(command != null && command.contains(" -n ABCD "))
    }
}
