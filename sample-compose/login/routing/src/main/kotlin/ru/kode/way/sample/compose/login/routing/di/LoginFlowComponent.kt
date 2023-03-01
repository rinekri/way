package ru.kode.way.sample.compose.login.routing.di

import dagger.Subcomponent
import ru.kode.way.NodeBuilder
import ru.kode.way.sample.compose.login.routing.LoginSchema
import ru.kode.way.sample.compose.permissions.routing.di.PermissionsFlowComponent
import javax.inject.Named

@Subcomponent(modules = [LoginFlowModule::class])
@LoginScope
interface LoginFlowComponent {
  @Named("login")
  fun nodeBuilder(): NodeBuilder
  fun schema(): LoginSchema

  fun permissionsFlowComponent(): PermissionsFlowComponent
}