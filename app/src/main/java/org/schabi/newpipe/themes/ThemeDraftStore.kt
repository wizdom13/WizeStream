package org.schabi.newpipe.themes

import android.content.Context
import java.io.File
import java.util.UUID

/** Keep large asset payloads out of the activity's Binder saved-state transaction. */
object ThemeDraftStore {
    fun save(context: Context, previous: String?, text: String?): String? {
        if (text == null) {
            remove(context, previous)
            return null
        }
        val id = previous?.takeIf(::valid) ?: UUID.randomUUID().toString()
        val directory = File(context.cacheDir, "theme-drafts").apply { mkdirs() }
        directory.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000 }?.forEach { it.delete() }
        File(directory, id).writeText(text, Charsets.UTF_8)
        return id
    }

    fun read(context: Context, id: String?): ThemePack? = try {
        if (id == null || !valid(id)) null else File(File(context.cacheDir, "theme-drafts"), id).inputStream().use(ThemePack::read)
    } catch (_: Exception) {
        null
    }

    fun remove(context: Context, id: String?) {
        if (id != null && valid(id)) File(File(context.cacheDir, "theme-drafts"), id).delete()
    }

    private fun valid(id: String): Boolean = id.matches(Regex("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}"))
}
