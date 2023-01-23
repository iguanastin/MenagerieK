package com.github.iguanastin.app

import java.rmi.Remote
import java.rmi.RemoteException
import java.rmi.registry.LocateRegistry
import java.rmi.registry.Registry
import java.rmi.server.ExportException
import java.rmi.server.UnicastRemoteObject

interface MenagerieRMICommunicator : Remote {

    @Throws(RemoteException::class)
    fun importUrl(url: String)

}

class MenagerieRMICommunicatorImpl(private val onImportURL: (url: String) -> Unit) : MenagerieRMICommunicator {

    override fun importUrl(url: String) {
        onImportURL(url)
    }

}

class MenagerieRMI(
    onServerStart: (rmi: MenagerieRMI) -> Unit = {},
    onClientStart: (rmi: MenagerieRMI) -> Unit = {},
    onImportURL: (url: String) -> Unit,
    onFailedConnect: (rmi: MenagerieRMI, e: Exception) -> Unit = { _, _ -> }
) {

    companion object {
        const val registryPort: Int = 1099
        const val rmiCommunicatorName = "communicator"
    }

    private lateinit var registry: Registry
    lateinit var communicator: MenagerieRMICommunicator
        private set

    init {
        try {
            registry = LocateRegistry.createRegistry(registryPort)
            communicator = MenagerieRMICommunicatorImpl(onImportURL)
            registry.bind(rmiCommunicatorName, UnicastRemoteObject.exportObject(communicator, 0))

            onServerStart(this)
        } catch (e: ExportException) {
            // Cannot open an RMI registry as Menagerie instance is already running
            try {
                // Open connection to existing RMI instance
                registry = LocateRegistry.getRegistry(registryPort)
                communicator = (registry.lookup(rmiCommunicatorName) as MenagerieRMICommunicator)

                onClientStart(this)
            } catch (e: Exception) {
                onFailedConnect(this, e)
            }
        }
    }

    fun close() {
        UnicastRemoteObject.unexportObject(communicator, true)
        registry.unbind(rmiCommunicatorName)
    }

}