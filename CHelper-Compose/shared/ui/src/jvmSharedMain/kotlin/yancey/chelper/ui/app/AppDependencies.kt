package yancey.chelper.ui.app

import yancey.chelper.ui.platform.ClientServices

/** Host bindings for the original application; feature state belongs to its original ViewModels. */
class AppDependencies(val services: ClientServices, val convert: suspend (String) -> String)
