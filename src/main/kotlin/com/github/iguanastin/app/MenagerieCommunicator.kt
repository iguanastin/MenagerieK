package com.github.iguanastin.app

import java.rmi.Remote
import java.rmi.RemoteException

interface MenagerieCommunicator: Remote {

    @Throws(RemoteException::class)
    fun importUrl(url: String)

}