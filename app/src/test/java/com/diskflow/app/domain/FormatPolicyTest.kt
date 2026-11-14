package com.diskflow.app.domain

import com.diskflow.app.domain.model.FileSystemType
import com.diskflow.app.domain.model.StorageCapacity
import com.diskflow.app.domain.model.StorageUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatPolicyTest {
    @Test
    fun `recommends filesystem for each main scenario`() {
        assertEquals(FileSystemType.EXFAT, FormatPolicy.recommend(StorageUseCase.CROSS_PLATFORM))
        assertEquals(FileSystemType.EXFAT, FormatPolicy.recommend(StorageUseCase.MEDIA))
        assertEquals(FileSystemType.NTFS, FormatPolicy.recommend(StorageUseCase.WINDOWS))
        assertEquals(FileSystemType.EXT4, FormatPolicy.recommend(StorageUseCase.LINUX_ANDROID))
    }

    @Test
    fun `legacy compatibility recommends FAT32 only when file fits`() {
        assertEquals(
            FileSystemType.FAT32,
            FormatPolicy.recommend(StorageUseCase.CROSS_PLATFORM, legacySupport = true),
        )
        assertEquals(
            FileSystemType.EXFAT,
            FormatPolicy.recommend(
                StorageUseCase.MEDIA,
                legacySupport = true,
                largestFileBytes = FormatPolicy.FAT32_MAX_FILE_BYTES + 1,
            ),
        )
        assertEquals(FileSystemType.NTFS, FormatPolicy.recommend(StorageUseCase.WINDOWS, true))
        assertEquals(FileSystemType.EXT4, FormatPolicy.recommend(StorageUseCase.LINUX_ANDROID, true))
    }

    @Test
    fun `FAT32 boundary is exactly four GiB minus one byte`() {
        assertEquals(4_294_967_295L, FormatPolicy.FAT32_MAX_FILE_BYTES)
        assertTrue(FormatPolicy.checkFileSize(FileSystemType.FAT32, FormatPolicy.FAT32_MAX_FILE_BYTES).isSupported)
        assertFalse(
            FormatPolicy.checkFileSize(FileSystemType.FAT32, FormatPolicy.FAT32_MAX_FILE_BYTES + 1)
                .isSupported,
        )
        assertFalse(FormatPolicy.checkFileSize(FileSystemType.FAT32, -1).isSupported)
        assertTrue(FormatPolicy.checkFileSize(FileSystemType.EXFAT, Long.MAX_VALUE).isSupported)
        assertTrue(FormatPolicy.checkFileSize(FileSystemType.NTFS, Long.MAX_VALUE).isSupported)
        assertTrue(FormatPolicy.checkFileSize(FileSystemType.EXT4, Long.MAX_VALUE).isSupported)
    }

    @Test
    fun `volume labels enforce common conservative rules`() {
        FileSystemType.entries.forEach { type ->
            assertTrue(FormatPolicy.validateVolumeLabel(type, "DISKFLOW").isValid)
            assertFalse(FormatPolicy.validateVolumeLabel(type, "   ").isValid)
        }
        assertFalse(FormatPolicy.validateVolumeLabel(FileSystemType.FAT32, "BAD/NAME").isValid)
        assertFalse(FormatPolicy.validateVolumeLabel(FileSystemType.NTFS, "BAD?NAME").isValid)
        assertTrue(FormatPolicy.validateVolumeLabel(FileSystemType.EXT4, "LINUX?DATA").isValid)
        assertFalse(FormatPolicy.validateVolumeLabel(FileSystemType.EXT4, "BAD/NAME").isValid)
        assertNotNull(FormatPolicy.validateVolumeLabel(FileSystemType.FAT32, "A".repeat(12)).errorMessage)
        assertTrue(FormatPolicy.validateVolumeLabel(FileSystemType.FAT32, "A".repeat(11)).isValid)
        assertTrue(FormatPolicy.validateVolumeLabel(FileSystemType.NTFS, "N".repeat(32)).isValid)
        assertFalse(FormatPolicy.validateVolumeLabel(FileSystemType.NTFS, "N".repeat(33)).isValid)
    }

    @Test
    fun `ext4 label limit uses UTF8 bytes rather than character count`() {
        assertTrue(FormatPolicy.validateVolumeLabel(FileSystemType.EXT4, "数据盘").isValid)
        assertFalse(FormatPolicy.validateVolumeLabel(FileSystemType.EXT4, "六个汉字标签").isValid)
    }

    @Test
    fun `allocation choices always include system default and supported values`() {
        FileSystemType.entries.forEach { type ->
            val options = FormatPolicy.allocationUnits(type)
            assertEquals(0, options.first().sizeKiB)
            assertTrue(options.size > 1)
            assertEquals(options.size, options.map { it.sizeKiB }.distinct().size)
        }
        assertEquals(listOf(0, 1, 2, 4), FormatPolicy.allocationUnits(FileSystemType.EXT4).map { it.sizeKiB })
        assertTrue(FormatPolicy.allocationUnits(FileSystemType.EXFAT).any { it.sizeKiB == 256 })
    }

    @Test
    fun `formats byte sizes in binary units`() {
        assertEquals("0 B", FormatPolicy.formatByteSize(0))
        assertEquals("1023 B", FormatPolicy.formatByteSize(1023))
        assertEquals("1.0 KiB", FormatPolicy.formatByteSize(1024))
        assertEquals("1.0 MiB", FormatPolicy.formatByteSize(1024L * 1024L))
        assertEquals("1.0 GiB", FormatPolicy.formatByteSize(1024L * 1024L * 1024L))
        assertEquals("4.0 GiB", FormatPolicy.formatByteSize(FormatPolicy.FAT32_MAX_FILE_BYTES))
    }

    @Test
    fun `capacity derives used bytes and bounded fraction`() {
        val capacity = StorageCapacity(totalBytes = 200L, availableBytes = 50L)
        assertEquals(150L, capacity.usedBytes)
        assertEquals(0.75f, capacity.usedFraction)
        assertEquals(0f, StorageCapacity(0L, 0L).usedFraction)
        assertNull(FormatPolicy.maximumSingleFileBytes(FileSystemType.EXFAT))
    }
}
