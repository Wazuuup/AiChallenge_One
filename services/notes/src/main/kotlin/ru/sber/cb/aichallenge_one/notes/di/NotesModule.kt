package ru.sber.cb.aichallenge_one.notes.di

import org.koin.dsl.module
import ru.sber.cb.aichallenge_one.notes.repository.NoteRepository
import ru.sber.cb.aichallenge_one.notes.service.NotesService

fun notesModule() = module {
    single { NoteRepository() }
    single { NotesService(noteRepository = get()) }
}
