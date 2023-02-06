package ru.kode.way.sample.compose.app.routing.di

import dagger.Module
import dagger.Provides
import dagger.Subcomponent
import ru.kode.way.NodeBuilder
import ru.kode.way.Schema
import ru.kode.way.sample.compose.login.routing.di.LoginFlowComponent
import javax.inject.Named
import javax.inject.Scope

@Scope
annotation class AppFlowScope

@Subcomponent(modules = [AppFlowModule::class])
@AppFlowScope
interface AppFlowComponent {
  fun loginFlowComponent(): LoginFlowComponent

  @Named("app") fun nodeBuilder(): NodeBuilder
  @Named("app") fun schema(): Schema
}

@Module
object AppFlowModule {
  @Provides
  @AppFlowScope
  @Named("app")
  fun provideNodeBuilder(component: AppFlowComponent): NodeBuilder {
    return component.loginFlowComponent().nodeBuilder()
  }

  @Provides
  @AppFlowScope
  @Named("app")
  fun provideSchema(component: AppFlowComponent): Schema {
    return component.loginFlowComponent().schema()
  }
}
