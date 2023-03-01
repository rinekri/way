package ru.kode.way.sample.compose.main.routing.di

import dagger.Module
import dagger.Provides
import ru.kode.way.NodeBuilder
import ru.kode.way.sample.compose.main.routing.HomeNode
import ru.kode.way.sample.compose.main.routing.MainFlowNode
import ru.kode.way.sample.compose.main.routing.MainNodeBuilder
import ru.kode.way.sample.compose.main.routing.MainSchema
import javax.inject.Named
import javax.inject.Provider
import javax.inject.Scope

@Scope
annotation class MainScope

@Module
object MainFlowModule {
  @Provides
  @MainScope
  @Named("main")
  fun provideNodeBuilder(
    flowNode: Provider<MainFlowNode>,
    homeNode: Provider<HomeNode>,
    schema: MainSchema,
  ): NodeBuilder {
    return MainNodeBuilder(
      nodeFactory = object : MainNodeBuilder.Factory {
        override fun createFlowNode() = flowNode.get()
        override fun createHomeNode() = homeNode.get()
      },
      schema = schema,
    )
  }

  @Provides
  @MainScope
  fun providesSchema(): MainSchema {
    return MainSchema()
  }
}