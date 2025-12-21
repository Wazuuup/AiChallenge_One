package ru.sber.cb.aichallenge_one.news.di

import org.koin.dsl.module
import ru.sber.cb.aichallenge_one.news.repository.NewsRepository
import ru.sber.cb.aichallenge_one.news.service.NewsService

val newsAppModule = module {
    single { NewsRepository() }
    single { NewsService(get()) }
}
