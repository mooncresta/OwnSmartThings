/*
 *  Domoticz (server)
 *
 *  Copyright 2015 Martin Verbeek
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
 
import groovy.json.*

definition(
    name: "Domoticz Server",
    namespace: "verbem",
    author: "Martin Verbeek",
    description: "Connects to local Domoticz server and define Domoticz devices in ST",
    category: "My Apps",
    iconUrl: "http://www.thermosmart.nl/wp-content/uploads/2015/09/domoticz-450x450.png",
//        iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",

    iconX2Url: "http://www.thermosmart.nl/wp-content/uploads/2015/09/domoticz-450x450.png",
    //iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png"
)
/*-----------------------------------------------------------------------------------------*/
/*		PREFERENCES      
/*-----------------------------------------------------------------------------------------*/
preferences {
    page name:"setupInit"
    page name:"setupMenu"
    page name:"setupDomoticz"
    page name:"setupListDevices"
    page name:"setupTestConnection"
    page name:"setupActionTest"
}
/*-----------------------------------------------------------------------------------------*/
/*		Mappings for REST ENDPOINT to communicate events from Domoticz      
/*-----------------------------------------------------------------------------------------*/
mappings {
    path("/EventDomoticz") {
        action: [ GET: "eventDomoticz" ]
    }
}

/*-----------------------------------------------------------------------------------------*/
/*		SET Up INIT
/*-----------------------------------------------------------------------------------------*/
private def setupInit() {
    TRACE("setupInit()")
    unsubscribe()
    subscribe(location, null, onLocation, [filterEvents:false])
    /*-------------------------------------------------------------------------------------*/
    /* get oAUTH token which you need to plug in Domoticz Notifications system
    /* to do this go to domotics Setup / settings / notifications and add it to the HTTP 
    /* custom notification with access_token=, together with the url for smartthings and #MESSAGE
	/* will look like this:
    /*
	/* https://graph.api.smartthings.com/api/smartapps/installations/put the ST App id here/EventDomoticz?access_token=put the oAuth token here&message=#MESSAGE
    /*
    /* to have a switch or other device in domoticz report back the status you have to activate the
    /* notifications on the device itself also for on and off (http)
    /* look in domoticz under switches and in every switch you will see the notifications 
    /*
    /*-------------------------------------------------------------------------------------*/

    if (!state.accessToken) {
        initRestApi()
    }
    if (state.setup) {
        // already initialized, go to setup menu
        return setupMenu()
    }
    /* 		Initialize app state and show welcome page */
    state.setup = [:]
    state.setup.installed = false
    state.devices = [:]
    
    return setupWelcome()
}
/*-----------------------------------------------------------------------------------------*/
/*		SET Up Welcome PAGE
/*-----------------------------------------------------------------------------------------*/
private def setupWelcome() {
    TRACE("setupWelcome()")

    def textPara1 =
        "Domoticz Bridge allows you integrate Domoticz defined devices into " +
        "SmartThings. Only support for blind and on/off devices now. Please note that it requires a server running " +
        "Domoticz installed on the local network and accessible from " +
        "the SmartThings hub.\n\n"
     

    def textPara2 = "${app.name}. ${textVersion()}\n${textCopyright()}"

    def textPara3 =
        "Please read the License Agreement below. By tapping the 'Next' " +
        "button at the top of the screen, you agree and accept the terms " +
        "and conditions of the License Agreement."

    def textLicense =
        "This program is free software: you can redistribute it and/or " +
        "modify it under the terms of the GNU General Public License as " +
        "published by the Free Software Foundation, either version 3 of " +
        "the License, or (at your option) any later version.\n\n" +
        "This program is distributed in the hope that it will be useful, " +
        "but WITHOUT ANY WARRANTY; without even the implied warranty of " +
        "MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU " +
        "General Public License for more details.\n\n" +
        "You should have received a copy of the GNU General Public License " +
        "along with this program. If not, see <http://www.gnu.org/licenses/>."

    def pageProperties = [
        name        : "setupInit",
        title       : "Welcome!",
        nextPage    : "setupMenu",
        install     : false,
        uninstall   : state.setup.installed
    ]

    return dynamicPage(pageProperties) {
        section {
            paragraph textPara1
            paragraph textPara3
        }
        section("License") {
            paragraph textLicense
        }
    }
}
/*-----------------------------------------------------------------------------------------*/
/*		SET Up Menu PAGE
/*-----------------------------------------------------------------------------------------*/
private def setupMenu() {
    TRACE("setupMenu()")

    // if domoticz is not configured, then do it now
    if (!settings.containsKey('domoticzIpAddress')) {
        return setupDomoticz()
    }

    def pageProperties = [
        name        : "setupMenu",
        title       : "Setup Menu",
        nextPage    : null,
        install     : true,
        uninstall   : state.setup.installed
    ]

    state.setup.deviceType = null

    return dynamicPage(pageProperties) {
        section {
            href "setupDomoticz", title:"Configure Domoticz Server", description:"Tap to open"
            href "setupTestConnection", title:"Add All Devices of a type", description:"Tap to open"
            if (state.devices.size() > 0) {
                href "setupListDevices", title:"List Installed Devices", description:"Tap to open"
            }
        }
        section([title:"Options", mobileOnly:true]) {
            label title:"Assign a name", required:false
        }
        section("About") {
            paragraph "${app.name}. ${textVersion()}\n${textCopyright()}"
        }
    }
}
/*-----------------------------------------------------------------------------------------*/
/*		SET Up Configure Domoticz PAGE
/*-----------------------------------------------------------------------------------------*/
private def setupDomoticz() {
    TRACE("setupDomoticz()")

    def textPara1 =
        "Enter IP address and TCP port of your Domoticz Server, then tap " +
        "Next to continue."

    def inputIpAddress = [
        name        : "domoticzIpAddress",
        type        : "string",
        title       : "Local Domoticz IP Address",
        defaultValue: "0.0.0.0"
    ]

    def inputTcpPort = [
        name        : "domoticzTcpPort",
        type        : "number",
        title       : "Local Domoticz TCP Port",
        defaultValue: "8080"
    ]

    def inputProtocol = [
        name        : "domoticzProtocol",
        type        : "enum",
        title       : "Devices you want to add",
        options	    : ["ALL", "Blinds", "On/Off"],
        defaultValue: "ALL"
    ]

    def inputTrace = [
        name        : "domoticzTrace",
        type        : "bool",
        title       : "Debug trace output in IDE log",
        defaultValue: true
    ]

    def pageProperties = [
        name        : "setupDomoticz",
        title       : "Configure Domoticz Server",
        nextPage    : "setupMenu",
        install     : false,
        uninstall   : false
    ]

    return dynamicPage(pageProperties) {
      section {
            input inputIpAddress
            input inputTcpPort
	        input inputProtocol
            input inputTrace
        	}
    }
}

/*-----------------------------------------------------------------------------------------*/
/*		SET Up Add Domoticz Devices PAGE
/*-----------------------------------------------------------------------------------------*/
private def setupTestConnection() {
    TRACE("setupTestConnection()")

    def textHelp =
        "Add all Domoticz devices that have the following type: " +
        "${domoticzProtocol}. Tap Next to continue."

    def pageProperties = [
        name        : "setupTestConnection",
        title       : "Add Domoticz Devices?",
        nextPage    : "setupActionTest",
        install     : false,
        uninstall   : false
    ]

    return dynamicPage(pageProperties) {
        section {
            paragraph textHelp
        }
    }
}
/*-----------------------------------------------------------------------------------------*/
/*		Execute Domoticz LIST devices from the server
/*-----------------------------------------------------------------------------------------*/
private def setupActionTest() {
    TRACE("setupActionTest()")

    def pageProperties = [
        name        : "setupActionTest",
        title       : "Adding Devices",
        nextPage    : "setupMenu",
        install     : false,
        uninstall   : false
    ]

    
    def networkId = makeNetworkId(settings.domoticzIpAddress, settings.domoticzTcpPort)
    
	socketSend("list", 0, 0)

    return dynamicPage(pageProperties) {
        section {
            paragraph "Executing Domoticz Add Devices, wait a few moments"
            paragraph "Tap Next to continue."
        }
    }
}
/*-----------------------------------------------------------------------------------------*/
/*		Predefined INSTALLED command
/*-----------------------------------------------------------------------------------------*/
def installed() {
    TRACE("installed()")

    initialize()
}
/*-----------------------------------------------------------------------------------------*/
/*		Predefined UPDATED command
/*-----------------------------------------------------------------------------------------*/
def updated() {
    TRACE("updated()")

    unsubscribe()
    initialize()
}

/*-----------------------------------------------------------------------------------------*/
/*		Predefined UNINSTALLED command
/*-----------------------------------------------------------------------------------------*/
def uninstalled() {
    TRACE("uninstalled()")

    // delete all child devices
    def devices = getChildDevices(true)
    devices?.each {
        try {
            deleteChildDevice(it.deviceNetworkId)
        } catch (e) {
            log.error "Cannot delete device ${it.deviceNetworkId}. Error: ${e}"
        }
    }
}
/*-----------------------------------------------------------------------------------------*/
/*		List the child devices in the SMARTAPP
/*-----------------------------------------------------------------------------------------*/
private def setupListDevices() {
    TRACE("setupListDevices()")

    def textNoDevices =
        "You have not configured any Domoticz devices yet. Tap Next to continue."

    def pageProperties = [
        name        : "setupListDevices",
        title       : "Connected Devices idx - name",
        nextPage    : "setupMenu",
        install     : false,
        uninstall   : false
    ]

    if (state.devices.size() == 0) {
        return dynamicPage(pageProperties) {
            section {
                paragraph textNoDevices
            }
        }
    }

    def switches = getDeviceListAsText('switch')
    return dynamicPage(pageProperties) {
        section {
            paragraph switches
        }
    }
}
/*-----------------------------------------------------------------------------------------*/
/*		Handle the location events that are being triggered from sendHubCommand
/*-----------------------------------------------------------------------------------------*/
def onLocation(evt) {
	log.info "onLocation evt.source ${evt.source}"

	TRACE("location event description is (${evt.description})")
    
    def description = evt.description
    def hMap = stringToMap(description)
    try {
        def header = new String(hMap.headers.decodeBase64())
        TRACE("location event header is (${header})")
    } catch (e) {
        return
    }
    
    def lstDomoticz = []
    def body = new String(hMap.body.decodeBase64())
    TRACE("location event body is (${body})")

    try {
    	def statusrsp = new JsonSlurper().parseText(body)
    } catch (e) {
        log.info("onLocation failed to parse Domoticz message - (${body})")
    }
    
    TRACE("statusrsp is (${statusrsp})")
    statusrsp = statusrsp.result
	statusrsp.each 
    	{ 
        	log.info("onLocation Before addswitch ${it.SwitchType} ${it.Name} ${it.Status}")
            if (it.SwitchType == domoticzProtocol || domoticzProtocol == "ALL") 
            {
                lstDomoticz.add(it.Name)
                switch (it.SwitchType) {
                case "Blinds":
                	addSwitch(it.idx, "domoticzBlinds", it.Name, it.Status)
                    break;
                case "On/Off":
                    TRACE("On-Off addSwitch IDX (${it.idx}), Name (${it.name})")
                    addSwitch(it.idx, "domoticzOnOff", it.Name, it.Status)
                    break;
                }
            }
            else 
            {
               TRACE("Not adding switch type (${it.SwitchType}), IDX (${it.idx}), Name (${it.name})")
            }
    	}

}

// Handle SmartApp touch event.
def onAppTouch(evt) {
    TRACE("onAppTouch(${evt})")
    STATE()
}

/*-----------------------------------------------------------------------------------------*/
/*		Subscribe and setup some initial stuff
/*-----------------------------------------------------------------------------------------*/
private def initialize() {
    log.trace "${app.name}. ${textVersion()}. ${textCopyright()}"
    STATE()

	// Subscribe to location events with filter disabled
    TRACE ("Subcribe to Location")
    subscribe(location, null, onLocation, [filterEvents:false])

    if (state.accessToken) {
        def url = getApiServerUrl() - ":443" + "/api/smartapps/installations/${app.id}/" + "EventDomoticz?access_token=" + state.accessToken + "&message=#MESSAGE"
        log.info url
        state.urlCustomActionHttp = url
	}

    state.setup.installed = true
    state.networkId = settings.domoticzIpAddress + ":" + settings.domoticzTcpPort
    updateDeviceList()

}

/*-----------------------------------------------------------------------------------------*/
/*		Execute the real add or status update of the child device
/*-----------------------------------------------------------------------------------------*/
private def addSwitch(addr, passedFile, passedName, passedStatus) {
    TRACE("Main addSwitch(${addr})")

    def dni = settings.domoticzIpAddress + ":" + settings.domoticzTcpPort + ":" + addr
    
    /*	the device already exists */
    if (getChildDevice(dni)) {
        TRACE("Main addSwitch(${addr}) DNI (${dni}) already exists")
       	return 
    }

    def devFile = passedFile
    def devParams = [
        name            : passedName,
        label           : passedName,
        status			: passedStatus,
        ip				: settings.domoticzIpAddress,
        port			: settings.domoticzTcpPort, 
        completedSetup  : true
    ]

    TRACE("Creating child device ${devParams}")
    try {
        def dev = addChildDevice("verbem", devFile, dni, location.hubs[0].id, devParams)
        dev.refresh()
          TRACE("addChildDevice(${addr}) success")
    } catch (e) {
        log.error "Cannot create child device. Error: ${e}"
        return 
    }

    // save device in the app state
    state.devices[addr] = [
        'dni'   : dni,
        'ip' : settings.domoticzIpAddress,
        'port' : settings.domoticzTcpPort,
        'idx' : addr,
        'type'  : 'switch',
        'deviceType' : devFile,
    ]

    STATE()
    return 
}
/*-----------------------------------------------------------------------------------------*/
/*		Purge devices that were removed manually
/*-----------------------------------------------------------------------------------------*/
private def updateDeviceList() {
    TRACE("updateDeviceList()")

    /* avoid ConcurrentModificationException that will happen */
     
    try {
        state.devices.each { k,v ->
            if (!getChildDevice(v.dni)) {
                TRACE("Removing deleted device ${v.dni}")
                state.devices.remove(k)
            }
        }
    } catch (e) {TRACE(e)}

    // refresh all devices
    def devices = getChildDevices(true)
    devices?.each {
        it.refresh()
    }
    
}

private def getDeviceMap() {
    def devices = [:]
    state.devices.each { k,v ->
        if (!devices.containsKey(v.type)) {
            devices[v.type] = []
        }
        devices[v.type] << k
    }

    return devices
}

private def getDeviceListAsText(type) {
    String s = ""
    state.devices.each { k,v ->
        TRACE("List dev - ${v}")
		if (v.type == type) {
            def dev = getChildDevice(v.dni)
            if (dev) {
                s += "${k.padLeft(4)} - ${dev.displayName}\n"
                TRACE("List string - ${s}")
            }
        }
    }

    return s
}

// Returns device Network ID in 'AAAAAAAA:PPPP' format
private String makeNetworkId(ipaddr, port) {
    TRACE("createNetworkId(${ipaddr}, ${port})")

    String hexIp = ipaddr.tokenize('.').collect {
        String.format('%02X', it.toInteger())
    }.join()

    String hexPort = String.format('%04X', port)
    return "${hexIp}:${hexPort}"
}

private def textVersion() {
    return "Version 1.0.0"
}

private def textCopyright() {
    return "Copyright (c) 2015 Martin Verbeek"
}

private def TRACE(message) {
    if(domoticzTrace) {log.debug message}
}

private def STATE() {
	if(domoticzTrace) {
    	log.debug "state: ${state}"
    	log.debug "settings: ${settings}"
        }
}


/*-----------------------------------------------------------------------------------------*/
/*		Excecute 'poll' command on behalf of child device
/*-----------------------------------------------------------------------------------------*/
def domoticz_poll(nid) {
	TRACE("domoticz poll(${nid})")
    socketSend("status", nid, 0)
}

/*-----------------------------------------------------------------------------------------*/
/*		Excecute 'off' command on behalf of child device
/*-----------------------------------------------------------------------------------------*/
def domoticz_off(nid) {
	TRACE("domoticz off(${nid})")
    socketSend("off", nid, 0)
}
/*-----------------------------------------------------------------------------------------*/
/*		Excecute 'on' command on behalf of child device
/*-----------------------------------------------------------------------------------------*/
def domoticz_on(nid) {
	TRACE("domoticz on(${nid})")
    socketSend("on", nid, 16)
}

/*-----------------------------------------------------------------------------------------*/
/*		Excecute 'toggle' command on behalf of child device
/*-----------------------------------------------------------------------------------------*/
def domoticz_toggle(nid) {
	TRACE("domoticz toggle(${nid})")
    socketSend("toggle", nid, 16)
}

/*-----------------------------------------------------------------------------------------*/
/*		Excecute 'stop' command on behalf of child device
/*-----------------------------------------------------------------------------------------*/
def domoticz_stop(nid) {
	TRACE("domoticz stop(${nid})")
    socketSend("stop", nid, 0)
}

/*-----------------------------------------------------------------------------------------*/
/*		Excecute 'setlevel' command on behalf of child device
/*-----------------------------------------------------------------------------------------*/
def domoticz_setlevel(nid, xLevel) {
	TRACE("domoticz setlevel(${nid})")
    if (xLevel.toInteger() == 0) {socketSend("off", nid, 0)}
    else {socketSend("on", nid, xLevel)}
}

/*-----------------------------------------------------------------------------------------*/
/*		Excecute The real request via the local HUB
/*-----------------------------------------------------------------------------------------*/
private def socketSend(message, addr, level) {
	def rooPath = ""
    def rooLog = ""
    TRACE("IDX = ${addr}") 
    
	switch (message) {
		case "list":
        	rooLog = "/json.htm?type=command&param=addlogmessage&message=SmartThings%20ListDevices"
        	rooPath = "/json.htm?type=devices&filter=light&used=true&order=Name"
 			break;
		case "status":
        	rooLog = "/json.htm?type=command&param=addlogmessage&message=SmartThings%20Status%20for%20${addr}"
			rooPath = "/json.htm?type=devices&rid=${addr}"
			break;
        case "off":
        	rooLog = "/json.htm?type=command&param=addlogmessage&message=SmartThings%20Off%20for%20${addr}"
        	rooPath = "/json.htm?type=command&param=switchlight&idx=${addr}&switchcmd=Off"
            break;
        case "toggle":
        	rooLog = "/json.htm?type=command&param=addlogmessage&message=SmartThings%20Toggle%20for%20${addr}"
        	rooPath = "/json.htm?type=command&param=switchlight&idx=${addr}&switchcmd=Toggle"
            break;
        case "on":
        	rooLog = "/json.htm?type=command&param=addlogmessage&message=SmartThings%20On%20for%20${addr}"
        	rooPath = "/json.htm?type=command&param=switchlight&idx=${addr}&switchcmd=On"
            break;
        case "stop":
        	rooLog = "/json.htm?type=command&param=addlogmessage&message=SmartThings%20Stop%20for%20${addr}"
        	rooPath = "/json.htm?type=command&param=switchlight&idx=${addr}&switchcmd=Stop"
            break;
        case "setlevel":
        	rooLog = "/json.htm?type=command&param=addlogmessage&message=SmartThings%20Level%20${level}%20for%20${addr}"
        	rooPath = "/json.htm?type=command&param=switchlight&idx=${addr}&switchcmd=On,level=${level}"
            break;
	}
    
    def hubActionLog = new physicalgraph.device.HubAction(
        method: "GET",
        path: rooLog,
        headers: [HOST: "${domoticzIpAddress}:${domoticzTcpPort}"])

    sendHubCommand(hubActionLog)
	
    def hubAction = new physicalgraph.device.HubAction(
        method: "GET",
        path: rooPath,
        headers: [HOST: "${domoticzIpAddress}:${domoticzTcpPort}"])

    sendHubCommand(hubAction)
	
}
/*-----------------------------------------------------------------------------------------*/
/*		Domoticz will send an event message to ST for all devices THAT HAVE BEEN SELECTED to do that
/*-----------------------------------------------------------------------------------------*/
def eventDomoticz() {
	TRACE("eventDomoticz" + params)
    if (params.message.contains(" >> ")) {
        def parts = params.message.split(" >> ")
        def devName = parts[0]
        def devStatus = parts[1]
        def children = getChildDevices()
		
        children.each { 
            TRACE(it.name)
            if (it.name == devName) {
               TRACE(it.typeName + "/" + devName + " changed to " + devStatus)
               switch (it.typeName) {
                    case "domoticzBlinds":
                        it.generateEvent("status":devStatus)
                    	break;

                    case "domoticzOnOff":
                        it.generateEvent("switch":devStatus)
	                    break;
                }
            }
        }
    }
    return null
}
/*-----------------------------------------------------------------------------------------*/
/*		get the access token. It will be displayed in the log/IDE. Plug this the Domoticz notification Settings access_token
/*-----------------------------------------------------------------------------------------*/
private def initRestApi() {
		TRACE("initRestApi")
        if (!state.accessToken) {
            def token = createAccessToken()
            TRACE("Created new access token: ${token}")
        }
        def url = getApiServerUrl() - ":443" + "/api/smartapps/installations/${app.id}/" + "EventDomoticz?access_token=" + state.accessToken + "&message=#MESSAGE"
        log.info url
        state.urlCustomActionHttp = url

}
//-----------------------------------------------------------
