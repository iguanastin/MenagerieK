package com.github.iguanastin.app

import java.rmi.Remote
import java.rmi.RemoteException

interface MenagerieRMICommunicator: Remote {

    @Throws(RemoteException::class)
    fun importUrl(url: String)

    @Throws(RemoteException::class)
    fun bringToFront()

}