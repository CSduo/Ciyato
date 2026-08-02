package com.ciyato.launcher

import com.ciyato.launcher.data.WorkspaceStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class WorkspaceStoreTest {

    @Test
    fun `legacy pager data migrates without losing either workspace`() {
        val layout = WorkspaceStore.migrateLegacy(
            count = 3,
            page0Apps = "com.example.left",
            page2Apps = "com.example.right",
            workspaceApps = "{}",
            workspaceCategories = "{\"0\":\"WORK\",\"2\":\"SOCIAL\"}",
            ciyatoPackage = "com.ciyato.launcher",
        )

        assertEquals(2, layout.workspaces.size)
        assertEquals("workspace-1", layout.workspaceAt(0)?.id)
        assertEquals(listOf("com.ciyato.launcher", "com.example.left"), layout.workspaceAt(0)?.appPackages)
        assertEquals(listOf("com.example.right"), layout.workspaceAt(1)?.appPackages)
        assertEquals(listOf("SOCIAL"), layout.workspaceAt(1)?.categoryKeys)
    }

    @Test
    fun `inserting before a workspace preserves creation identity`() {
        val original = WorkspaceStore.migrateLegacy(3, "", "", "{}", "{}", "com.ciyato.launcher")
        val inserted = requireNotNull(WorkspaceStore.insert(original, 0))

        assertEquals(listOf("workspace-3", "workspace-1", "workspace-2"), inserted.visualOrder)
        assertEquals(1, inserted.workspaces.first { it.id == "workspace-1" }.creationOrder)
        assertEquals(3, inserted.workspaces.first { it.id == "workspace-3" }.creationOrder)
    }

    @Test
    fun `removing a workspace can transfer its shortcuts without renumbering`() {
        val initial = WorkspaceStore.migrateLegacy(3, "com.example.left", "com.example.right", "{}", "{}", "com.ciyato.launcher")
        val result = requireNotNull(WorkspaceStore.remove(initial, "workspace-1", "workspace-2"))

        assertEquals(listOf("workspace-2"), result.visualOrder)
        assertEquals(
            listOf("com.example.right", "com.ciyato.launcher", "com.example.left"),
            result.workspaceAt(0)?.appPackages,
        )
        assertNotNull(WorkspaceStore.parse(WorkspaceStore.serialize(result)))
    }

    @Test
    fun `duplicate inserts a new permanent identity directly after its source`() {
        val initial = WorkspaceStore.migrateLegacy(3, "com.example.left", "com.example.right", "{}", "{}", "com.ciyato.launcher")
        val result = requireNotNull(WorkspaceStore.duplicate(initial, "workspace-1"))

        assertEquals(listOf("workspace-1", "workspace-3", "workspace-2"), result.visualOrder)
        assertEquals(listOf("com.ciyato.launcher", "com.example.left"), result.workspaceAt(1)?.appPackages)
        assertEquals(3, result.workspaceAt(1)?.creationOrder)
    }

    @Test
    fun `default workspace is persisted independently from visual order`() {
        val initial = WorkspaceStore.migrateLegacy(3, "", "", "{}", "{}", "com.ciyato.launcher")
        val result = requireNotNull(WorkspaceStore.setDefault(initial, "workspace-2"))

        assertEquals("workspace-2", result.defaultWorkspaceId)
        assertNotNull(WorkspaceStore.parse(WorkspaceStore.serialize(result)))
    }

    @Test
    fun `moving a shortcut reorders only the selected workspace grid`() {
        val initial = WorkspaceStore.migrateLegacy(
            count = 3,
            page0Apps = "com.example.one,com.example.two,com.example.three",
            page2Apps = "com.example.other",
            workspaceApps = "{}",
            workspaceCategories = "{}",
            ciyatoPackage = "com.ciyato.launcher",
        )

        val result = requireNotNull(
            WorkspaceStore.moveAppWithinWorkspace(
                layout = initial,
                workspaceId = "workspace-1",
                packageName = "com.example.one",
                destinationIndex = 2,
            ),
        )

        assertEquals(
            listOf("com.ciyato.launcher", "com.example.two", "com.example.one", "com.example.three"),
            result.workspaceAt(0)?.appPackages,
        )
        assertEquals(listOf("com.example.other"), result.workspaceAt(1)?.appPackages)
    }

    @Test
    fun `moving a shortcut toward the end preserves every unique shortcut`() {
        val initial = WorkspaceStore.migrateLegacy(
            count = 3,
            page0Apps = "com.example.one,com.example.two,com.example.three",
            page2Apps = "com.example.other",
            workspaceApps = "{}",
            workspaceCategories = "{}",
            ciyatoPackage = "com.ciyato.launcher",
        )

        val result = requireNotNull(
            WorkspaceStore.moveAppWithinWorkspace(
                layout = initial,
                workspaceId = "workspace-1",
                packageName = "com.ciyato.launcher",
                destinationIndex = 2,
            ),
        )

        assertEquals(
            listOf("com.example.one", "com.example.two", "com.ciyato.launcher", "com.example.three"),
            result.workspaceAt(0)?.appPackages,
        )
        assertEquals(4, result.workspaceAt(0)?.appPackages?.distinct()?.size)
    }

    @Test
    fun `version 1 layout upgrades to positioned cells without losing order`() {
        val v1 = """{"version":1,"defaultWorkspaceId":"workspace-1","visualOrder":["workspace-1"],""" +
            """"workspaces":[{"id":"workspace-1","creationOrder":1,"name":"W1",""" +
            """"appPackages":["com.a","com.b","com.c"],"categoryKeys":[],"starterDismissed":false}]}"""
        val layout = requireNotNull(WorkspaceStore.parse(v1))
        assertEquals(listOf("com.a", "com.b", "com.c"), layout.workspaceAt(0)?.appPackages)
        // Re-serialized as v2 and re-parsed, the order is identical (lossless upgrade).
        val reparsed = requireNotNull(WorkspaceStore.parse(WorkspaceStore.serialize(layout)))
        assertEquals(listOf("com.a", "com.b", "com.c"), reparsed.workspaceAt(0)?.appPackages)
    }

    @Test
    fun `placing an app at a specific cell leaves a gap`() {
        val initial = WorkspaceStore.migrateLegacy(3, "com.a,com.b", "", "{}", "{}", "com.ciyato.launcher")
        // workspace-1 = [ciyato@0, com.a@1, com.b@2]; move com.b to cell 5.
        val result = requireNotNull(WorkspaceStore.placeApp(initial, "workspace-1", "com.b", 5))
        val cells = result.workspaceAt(0)!!.cells.associate { it.packageName to it.cell }
        assertEquals(5, cells["com.b"])
        assertEquals(0, cells["com.ciyato.launcher"])
        assertEquals(1, cells["com.a"])
    }

    @Test
    fun `reordering a workspace never changes its default identity`() {
        val initial = WorkspaceStore.migrateLegacy(3, "", "", "{}", "{}", "com.ciyato.launcher")
        val defaulted = requireNotNull(WorkspaceStore.setDefault(initial, "workspace-2"))
        val result = requireNotNull(WorkspaceStore.reorder(defaulted, sourceIndex = 1, destinationIndex = 0))

        assertEquals(listOf("workspace-2", "workspace-1"), result.visualOrder)
        assertEquals("workspace-2", result.defaultWorkspaceId)
        assertNotNull(WorkspaceStore.parse(WorkspaceStore.serialize(result)))
    }
}
