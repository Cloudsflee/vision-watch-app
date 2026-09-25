设备蓝牙连接
进行设备连接之前，首先要获取相关的蓝牙权限，并确保蓝牙可用。

系统蓝牙
Tips:参考示例代码中的：MainActivity.kt 、MainViewModel.kt 与CONSTANT.kt

1 获取BluetoothManager
// In Activity
private lateinit var bluetoothManager: BluetoothManager

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // .....
    // other codes
    // ....
    bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
    // To check permissions
    // viewModel.checkPermission(this, bluetoothManager)
}
2 检查权限
蓝牙必要权限：

@SuppressLint("ObsoleteSdkInt")
val BLUETOOTH_PERMISSIONS = mutableListOf(
    android.Manifest.permission.BLUETOOTH,
    android.Manifest.permission.BLUETOOTH_ADMIN,
    android.Manifest.permission.ACCESS_COARSE_LOCATION,
    android.Manifest.permission.ACCESS_FINE_LOCATION
).apply {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(android.Manifest.permission.BLUETOOTH_CONNECT)
        add(android.Manifest.permission.BLUETOOTH_SCAN)
    }
}.toTypedArray()
检查权限：

// In ViewModel
/**
 * Check bluetooth permission
 */
fun checkPermission(context: Context, manager: BluetoothManager) {
    if (CONSTANT.BLUETOOTH_PERMISSIONS.all {
            val result = ActivityCompat.checkSelfPermission(
                context,
                it
            ) == PackageManager.PERMISSION_GRANTED
            Log.e("Permission", "permission = $it, result = $result")
            result
        }) {
        // If result is true，to make sure System Bluetooth is enabled
        //checkBluetoothEnabled(manager.adapter)
    } else {
        // If result is false means need get Bluetooth Permission
        //_bluetoothState.value = BluetoothState.PERMISSION_REQUIRED
    }
}
3 申请权限
动态申请权限的方法有很多，这里以ActivityResultLauncher为例。

// In Activity
// Request permission results from ActivityResult
private val requestBluetoothPermission = registerForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
) { permissions ->
   // re-check permissions
   // viewModel.checkPermission(this@MainActivity, bluetoothManager)
}


// In ViewModel
/**
 * Request bluetooth permission
 * @param launcher ActivityResultLauncher<Array<String>> To start permission request
 */
fun requestBluetoothPermission(launcher: ActivityResultLauncher<Array<String>>) {
    launcher.launch(CONSTANT.BLUETOOTH_PERMISSIONS)
}
4 检查系统蓝牙是否已开启
/**
 * Check bluetooth enabled
 * @param bluetoothAdapter BluetoothAdapter
 */
fun checkBluetoothEnabled(bluetoothAdapter: BluetoothAdapter?) {
    _bluetoothState.value = bluetoothAdapter?.let {
        if (it.isEnabled) {
            // Bluetooth Enabled Check PASS
            // BluetoothState.BLUETOOTH_READY
        } else {
			// System Bluetooth does not open
            // BluetoothState.BLUETOOTH_DISABLED
        }
    } ?: run {
        // non-blue tooth adapter
        // BluetoothState.BLUETOOTH_DISABLED
    }
}
5 打开系统蓝牙
通过调起蓝牙系统设置来打开蓝牙。这里使用ActivityResultLauncher为例。

// In Activity
private val openBluetoothLauncher = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult()
) {result ->
    if (result.resultCode == RESULT_OK){// Bluetooth Opened
        // re-check adapter status
        // .....
        // other code
        // ....
    }
}

// In ViewModel
/**
 * Request bluetooth enable
 * @param launcher ActivityResultLauncher<Intent> To start activity for result
 */
fun requestBluetoothEnable(launcher: ActivityResultLauncher<Intent>) {
    launcher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
}
扫描
在确认系统蓝牙处于打开状态后，就可以进行蓝牙设备的扫描了。

Tips:参考示例代码中的：BluetoothInitActivity.kt、BluetoothIniViewModel.kt与CONSTANT.kt

Tips：注意，如果是与本应用连接过的设备，并且连接成功后未执行过折叠镜腿并三击功能键进入过配对状态的设备，不需要再进行下列操作。

1 使眼镜进入可被扫描状态
应用首次连接，或者从其他应用（比如设备与Rokid AI APP 建立过连接）切换到到当前应用，需要将眼镜设置为可被扫描状态，以便在移动端可以扫描到当前眼镜。

方法：折叠镜腿，并三击镜腿上的功能键。

make glasses scannable

2 扫描设备
扫描眼镜设备，使用BLE 服务扫描方法即可，即使用BluetoothLeScanner。

眼镜端在进入可被扫描状态后，会启动一个UUID 为00009100-0000-1000-8000-00805f9b34fb 的BLE 服务，可以通过过滤改UUID 的方法识别周边是否有Rokid 眼镜设备。

// Callback for BLE scan
private val bleScannerCallback: ScanCallback = object : ScanCallback() {
    /**
     * Called when a BLE device is found
     *
     */
    @SuppressLint("MissingPermission")
    override fun onScanResult(callbackType: Int, result: android.bluetooth.le.ScanResult) {
        super.onScanResult(callbackType, result)
        val device = result.device
        // .....
        // other code
        // ....
    }

    /**
     * Called when a BLE scan fails
     *
     */
    override fun onScanFailed(errorCode: Int) {
        Log.e(TAG, "BLE scan failed with error code: $errorCode")
    }
}

/**
 * Handle BLE scanning status, if it is currently scanning, stop it, otherwise start a new scan
 * @param bleScanner The BluetoothLeScanner instance
 */
@SuppressLint("MissingPermission")
fun handleScan(bleScanner: BluetoothLeScanner?) {
    // .....
    // other code
    // ....
    // Create a filter to match devices with the specified service UUID
    val filter =
        ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(CONSTANT.SERVICE_UUID))
            .build()
    val scanSettings =
        ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
    bleScanner?.startScan(mutableListOf(filter), scanSettings, bleScannerCallback)
	// .....
    // other code
    // ....
}
连接眼镜
Tips:参考示例代码中的：BluetoothInitActivity.kt、BluetoothIniViewModel.kt与CONSTANT.kt

1 初始化蓝牙连接
Tips：注意，如果是与本应用连接过的设备，并且连接成功后未执行过折叠镜腿并三击功能键进入过配对状态的设备，不需要再进行下列操作。

扫描到蓝牙设备后，就通过眼镜蓝牙初始化接口CxrApi.getInstance().initBluetooth(context: Context, device: BluetoothDevice, cbk: BluetoothStatusCallback)，对蓝牙进行初始化。

其中BluetoothStatusCallback是：

fun onConnectionInfo(uuid: String?, macAddress: String?, rokidAccount: String?, glassesType: Int)设备连接必要信息；
参数uuid：初始化成功后从眼镜端发送的Socket UUID；
参数macAddress：初始化成功后从眼镜端发送的Socket MAC Address；
参数rokidAccount：在使用SDK 过程中不需要的字段；
参数glassType：眼镜类型，其中0 表示没有显示屏的眼镜，1 表示带显示屏的眼镜。
fun onConnected()设备连接成功回调；
fun onDisconnected()设备连接断开回调；
fun onFailed(error: ValueUtil.CxrBluetoothErrorCode?)设备连接失败时的回调。
参数error：
ValueUtil.CxrBluetoothErrorCode.PARAM_INVALID 非法参数
ValueUtil.CxrBluetoothErrorCode.BLE_CONNECT_FAILED 蓝牙初始化过程中BLE 服务连接失败
ValueUtil.CxrBluetoothErrorCode.SOCKET_CONNECT_FAILED 蓝牙连接过程中蓝牙Socket 连接失败
ValueUtil.CxrBluetoothErrorCode.SN_CHECK_FAILED Serial number check failed 蓝牙连接过程中SN 鉴权失败
ValueUtil.CxrBluetoothErrorCode.UNKNOWN Unknown error 未知错误
// Bluetooth connection state callback
private val connectionState = object : BluetoothStatusCallback {
    /**
     * Called when a Bluetooth device connects or disconnects
     * @param uuid The UUID of the device
     * @param macAddress The MAC address of the device
     * @param p2 rokid account, do not use it if you don't need it
     * @param glassesType Type of glasses, 0 -- non-display, 1 -- have display
     */
    override fun onConnectionInfo(
        uuid: String?,
        macAddress: String?,
        p2: String?,
        glassesType: Int
    ) {
        Log.d(TAG, "onConnectionInfo: uuid=$uuid, macAddress=$macAddress, p2=$p2, p3=${if (glassesType == 1) "Display glasses" else "Non-display glasses"}")
        //......
        // other codes
        //......
    }

    /**
     * Called when a Bluetooth device connects successfully
     *
     */
    override fun onConnected() {
        Log.d(TAG, "Bluetooth device connected successfully")
        //......
        // other codes
        //......
    }

    /**
     * Called when a Bluetooth device disconnects
     *
     */
    override fun onDisconnected() {
        Log.d(TAG, "Bluetooth device disconnected")
        //......
        // other codes
        //......
    }

    /**
     * Called when a Bluetooth device connection fails
     * @param p0 The error code:
     *  @see ValueUtil.CxrBluetoothErrorCode.PARAM_INVALID  Invalid parameter
     *  @see ValueUtil.CxrBluetoothErrorCode.BLE_CONNECT_FAILED Bluetooth connection failed
     *  @see ValueUtil.CxrBluetoothErrorCode.SOCKET_CONNECT_FAILED Socket connection failed
     *  @see ValueUtil.CxrBluetoothErrorCode.SN_CHECK_FAILED Serial number check failed
     *  @see ValueUtil.CxrBluetoothErrorCode.UNKNOWN Unknown error
     */
    override fun onFailed(p0: ValueUtil.CxrBluetoothErrorCode?) {
        Log.e(TAG, "Bluetooth connection failed with error: $p0")
        _connecting.value = false
        _connected.value = false
    }
}
/**
 * Init Bluetooth connection after a device in fount device list is clicked
 * @param deviceItem The selected device item
 */
fun deviceClicked(context: Context, deviceItem: DeviceItem?) {
    deviceItem?.let {
        Log.d(TAG, "Device clicked: name=${it.name}, address=${it.macAddress}")
        _recordName.value = it.name
        // init bluetooth when make connection of glasses the first time.
        CxrApi.getInstance().initBluetooth(context, it.device, connectionState)
        _connecting.value = true
    }
}
2 蓝牙连接眼镜
TIPS：如果是与本应用连接过的设备，并且连接成功后未执行过折叠镜腿并三击功能键进入过配对状态的设备，可以直接使用保存的UUID 和MacAddress 进行如下的连接。

在从初始化回调中的onConnectionInfo或者已保存中的信息中获取到UUID 和MacAddress，并已获取了Client Secret 和SN 授权文件后，可以调用CxrApi.getInstance().connectBluetooth(context:Context, uuid: String, macAddress: String, cbk: BluetoothStatusCallback, SNLC: ByteArray, clientSecret: String)进行连接。

其中Context 是连接时的上下文；

其中uuid：初始化回调中的onConnectionInfo或者已保存中的信息中获取到UUID；

其中macAddress：初始化回调中的onConnectionInfo或者已保存中的信息中获取到Mac Address；

其中BluetoothStatusCallback是：

fun onConnectionInfo(uuid: String?, macAddress: String?, rokidAccount: String?, glassesType: Int)设备连接必要信息；
参数uuid：初始化成功后从眼镜端发送的Socket UUID；
参数macAddress：初始化成功后从眼镜端发送的Socket MAC Address；
参数rokidAccount：在使用SDK 过程中不需要的字段；
参数glassType：眼镜类型，其中0 表示没有显示屏的眼镜，1 表示带显示屏的眼镜。
fun onConnected()设备连接成功回调；
fun onDisconnected()设备连接断开回调；
fun onFailed(error: ValueUtil.CxrBluetoothErrorCode?)设备连接失败时的回调。
参数error：
ValueUtil.CxrBluetoothErrorCode.PARAM_INVALID 非法参数
ValueUtil.CxrBluetoothErrorCode.BLE_CONNECT_FAILED 蓝牙初始化过程中BLE 服务连接失败
ValueUtil.CxrBluetoothErrorCode.SOCKET_CONNECT_FAILED 蓝牙连接过程中蓝牙Socket 连接失败
ValueUtil.CxrBluetoothErrorCode.SN_CHECK_FAILED Serial number check failed 蓝牙连接过程中SN 鉴权失败
ValueUtil.CxrBluetoothErrorCode.UNKNOWN Unknown error 未知错误
其中SNLC：是读取的SN 授权文件的Byte 数组；

其中clientSecret：是开发者账号中凭证信息中的Client Secret。

// Bluetooth connection state callback
private val connectionState = object : BluetoothStatusCallback {
    /**
     * Called when a Bluetooth device connects or disconnects
     * @param uuid The UUID of the device
     * @param macAddress The MAC address of the device
     * @param p2 rokid account, do not use it if you don't need it
     * @param glassesType Type of glasses, 0 -- non-display, 1 -- have display
     */
    override fun onConnectionInfo(
        uuid: String?,
        macAddress: String?,
        p2: String?,
        glassesType: Int
    ) {
        Log.d(TAG, "onConnectionInfo: uuid=$uuid, macAddress=$macAddress, p2=$p2, p3=${if (glassesType == 1) "Display glasses" else "Non-display glasses"}")
        //......
        // other codes
        //......
    }

    /**
     * Called when a Bluetooth device connects successfully
     *
     */
    override fun onConnected() {
        Log.d(TAG, "Bluetooth device connected successfully")
        //......
        // other codes
        //......
    }

    /**
     * Called when a Bluetooth device disconnects
     *
     */
    override fun onDisconnected() {
        Log.d(TAG, "Bluetooth device disconnected")
        //......
        // other codes
        //......
    }

    /**
     * Called when a Bluetooth device connection fails
     * @param p0 The error code:
     *  @see ValueUtil.CxrBluetoothErrorCode.PARAM_INVALID  Invalid parameter
     *  @see ValueUtil.CxrBluetoothErrorCode.BLE_CONNECT_FAILED Bluetooth connection failed
     *  @see ValueUtil.CxrBluetoothErrorCode.SOCKET_CONNECT_FAILED Socket connection failed
     *  @see ValueUtil.CxrBluetoothErrorCode.SN_CHECK_FAILED Serial number check failed
     *  @see ValueUtil.CxrBluetoothErrorCode.UNKNOWN Unknown error
     */
    override fun onFailed(p0: ValueUtil.CxrBluetoothErrorCode?) {
        Log.e(TAG, "Bluetooth connection failed with error: $p0")
        _connecting.value = false
        _connected.value = false
    }
}
/**
 * Connect to Glasses's socket, the last step of the connection process
 */
fun connectBTSocket(context: Context) {
    Log.d(TAG, "Reconnecting to device: uuid=${_recordUUID.value}, mac=${_recordMacAddress.value}")
    // Reconnect/Connect(first time) to the device
    try {
        CxrApi.getInstance().connectBluetooth(
            context,
            _recordUUID.value ?: "error", // uuid from record or BluetoothStatusCallback::onConnectionInfo
            _recordMacAddress.value ?: "error", // mac from record or BluetoothStatusCallback::onConnectionInfo
            connectionState, // callback for connection state
            readRawFile(context), // SN authentication file
            CONSTANT.CLIENT_SECRET.replace("-", "") // client secret
        )
    }catch (e: Exception){
        Log.d(TAG, "Error: ${e.message}")
        e.printStackTrace()
    }

}
至此，就完成了Glasses 与应用的连接过程。

获取设备信息
在获取信息之前，请先完成设备连接。

Tips:参考示例代码中的：DeviceInformationActivity.kt 、DeviceInformationViewModel.kt

获取全部信息
可以通过CxrApi.getInstance().getGlassInfo(cbk: GlassInfoResultCallback)接口来获取设备的全部信息。

GlassInfoResultCallback接口中的fun onGlassInfoResult(responseStatus: ValueUtil.CxrStatus, info: GlassInfo)回调返回中的GlassInfo就是目前给到的全部信息。

GlassInfoResultCallback

fun onGlassInfoResult(responseStatus: ValueUtil.CxrStatus, info: GlassInfo)：结果回调；
CxrStatus: 消息回调状态
CxrStatus.RESPONSE_SUCCEED: 响应成功
CxrStatus.RESPONSE_INVALID: 响应异常
CxrStatus.RESPONSE_TIMEOUT: 响应超时
GlassInfo: 设备信息
deviceName: 设备名称
batteryLevel: 电池电量
isCharging: 是否正在充电
brightness: 亮度
sound: 音量
wearingStatus: 佩戴状态
deviceId: 设备SN
systemVersion: 系统版本
// Get Glass Information
private val glassInfoCallback = GlassInfoResultCallback { status, glassInfo ->
    if (status == ValueUtil.CxrStatus.RESPONSE_SUCCEED){
        glassInfo?.let {info ->
            _deviceName.value = info.deviceName // Device Name
            _deviceId.value = info.deviceId // Device ID
            _systemVersion.value = info.systemVersion // System Version
            _wearingState.value = info.wearingStatus  // Wearing State
            _brightness.value = info.brightness // Brightness
            _setBrightness.value = info.brightness 
            _soundVolume.value = info.volume // Sound Volume
            _setVolume.value = info.volume
            _batteryLevel.value = info.batteryLevel // Battery Level
            _isCharging.value = info.isCharging // Is Charging
            Log.i(TAG, "glassInfo = $info")
        }
    }else{
        Log.e(TAG, "getGlassInfo failed: ${status.name}")
    }
}
/**
 * Get All Device Information
 */
fun getDeviceInformation(){
    CxrApi.getInstance().getGlassInfo(glassInfoCallback)
}
监听电量变化
可以通过CxrApi.getInstance().setBatteryLevelUpdateListener(callback: BatteryLevelUpdateListener?)来设置或者移除电量监听。

BatteryLevelUpdateListener

fun onBatteryLevelUpdated(level: Int, isCharging: Boolean)电量变化回调
level:电量，范围是【0-100】
isCharging:是否充电中
可以通过CxrApi.getInstance().setBatteryLevelUpdateListener(null)将电量监听移除。

/**
 * Listen for battery level changes
 *
 * @param toSet true: Set listener; false: Cancel listener
 */
fun toSetBatteryListener(){
    val toSet = !_batteryListenerSet.value
    CxrApi.getInstance().setBatteryLevelUpdateListener(if (toSet)  {
        BatteryLevelUpdateListener { level, isCharging -> // level range 0-100
            _batteryLevel.value = level // Battery Level
            _isCharging.value = isCharging // Charging Status
        }
    } else null)
    _batteryListenerSet.value = toSet
}
设置与监听亮度
1 亮度监听
可以通过CxrApi.getInstance().setBrightnessUpdateListener(callback: BrightnessUpdateListener?)来设置或者移除亮度监听。

BrightnessUpdateListener

fun onBrightnessUpdated(level: Int)亮度变化回调
level:亮度，范围是【0-15】
可以通过CxrApi.getInstance().setBrightnessUpdateListener(null)将亮度监听移除。

/**
 * Listen for brightness changes
 * 
 */
fun toSetBrightnessListener(){
    val toSet = !_brightnessListenerSet.value // true: Set listener, false: Cancel listener
    CxrApi.getInstance().setBrightnessUpdateListener (if (toSet) {
        BrightnessUpdateListener { level ->
            _brightness.value = level // Brightness
            _setBrightness.value = level // Set Brightness
        }
    } else{
        _brightness.value = -1
        null
    })
    _brightnessListenerSet.value = toSet
}
2 亮度设置
可以通过CxrApi.getInstance().setGlassBrightness(level: Int): ValueUtil.CxrStatus来设置亮度。

亮度范围必须在【0-15】之间。

返回值ValueUtil.CxrStatus：

ValueUtil.CxrStatus.REQUEST_SUCCEED：设置成功
ValueUtil.CxrStatus.REQUEST_FAILED：设置失败
ValueUtil.CxrStatus.REQUEST_WAITING：设置指令已发出，但眼镜系统繁忙
/**
 * Set brightness
 * @param level brightness level, Range [0-15]
 */
fun setBrightness(level: Int){
    // Validate brightness level, level must be in range [0, 15]
    val validLevel = level.coerceIn(0, 15)
    // Set brightness
    when(CxrApi.getInstance().setGlassBrightness(validLevel)){
        ValueUtil.CxrStatus.REQUEST_SUCCEED -> {// Set brightness succeed
            Log.i(TAG, "setBrightness succeed")
        }
        ValueUtil.CxrStatus.REQUEST_FAILED -> {// Set brightness failed
            Log.e(TAG, "setBrightness failed")
        }
        ValueUtil.CxrStatus.REQUEST_WAITING -> {// Set brightness requested, but Glass is busy
            Log.e(TAG, "Set Brightness Command is in queue!")
        }
        else -> {// Unknown status
            Log.e(TAG, "Unknown status")
        }
    }
}
设置与监听音量
1 音量监听
可以通过CxrApi.getInstance().setVolumeUpdateListener(callback: VolumeUpdateListener?)来设置或者移除音量监听。

VolumeUpdateListener

fun onVolumeUpdated(level: Int)音量变化回调
level:亮度，范围是【0-15】
可以通过CxrApi.getInstance().setVolumeUpdateListener(null)将音量监听移除。

/**
 * Listen for volume changes
 * 
 */
fun toSetSoundVolumeListener(){
    val toSet = !_volumeListenerSet.value // true: Set listener, false: Cancel listener
    CxrApi.getInstance().setVolumeUpdateListener(if (toSet) {
        VolumeUpdateListener { level ->
            _soundVolume.value = level // Sound Volume
            _setVolume.value = level // Set Volume
        }
    } else{
        _soundVolume.value = -1
        null
    })
    _volumeListenerSet.value = toSet
}
2 音量设置
可以通过CxrApi.getInstance().setGlassVolume(level: Int): ValueUtil.CxrStatus来设置音量。

音量范围必须在【0-15】之间。

返回值ValueUtil.CxrStatus：

ValueUtil.CxrStatus.REQUEST_SUCCEED：设置成功
ValueUtil.CxrStatus.REQUEST_FAILED：设置失败
ValueUtil.CxrStatus.REQUEST_WAITING：设置指令已发出，但眼镜系统繁忙
/**
 * Set volume
 * @param level volume level Range(0-15)
 */
fun setSoundVolume(level: Int){
    val validLevel = level.coerceIn(0, 15) // Validate volume level, level must be in range [0, 15]
    when(CxrApi.getInstance().setGlassVolume(validLevel)){
        ValueUtil.CxrStatus.REQUEST_SUCCEED -> { // Set volume succeed
            Log.i(TAG, "setSoundVolume succeed")
        }
        ValueUtil.CxrStatus.REQUEST_FAILED -> { // Set volume failed
            Log.e(TAG, "setSoundVolume failed")
        }
        ValueUtil.CxrStatus.REQUEST_WAITING -> { // Set volume requested, but Glass is busy
            Log.e(TAG, "Set Volume Command is in queue!")
        }
        else -> { // Unknown status
            Log.e(TAG, "Unknown status")
        }
    }
}
监听屏幕状态与关闭屏幕
1 屏幕状态监听
可以通过CxrApi.getInstance().setScreenStatusUpdateListener(callback: ScreenStatusUpdateListener?)来设置或者移除音量监听。

ScreenStatusUpdateListener

fun onScreenStatusUpdated(screenOn: boolean)屏幕状态变化监听
screenOn:屏幕点亮状态
true: 屏幕亮屏
false: 屏幕熄屏
可以通过CxrApi.getInstance().setScreenStatusUpdateListener(null)将屏幕状态监听移除。

/**
 * Listen for screen status changes
 *
 */
fun toSetScreenListener(){
    val toSet = !_screenListenerSet.value // true: Set listener, false: Cancel listener
    CxrApi.getInstance().setScreenStatusUpdateListener(if (toSet) {
        ScreenStatusUpdateListener { isScreenOn ->
            _isScreenOn.value = isScreenOn // Screen Status
            _screenListenerSet.value = true
        }
    } else{
        _screenListenerSet.value = false
        null
    })
    _screenListenerSet.value = toSet
}
2 熄屏
可以通过CxrApi.getInstance().notifyGlassScreenOff(): ValueUtil.CxrStatus来通知屏幕熄屏。

返回值ValueUtil.CxrStatus：

ValueUtil.CxrStatus.REQUEST_SUCCEED：设置成功
ValueUtil.CxrStatus.REQUEST_FAILED：设置失败
ValueUtil.CxrStatus.REQUEST_WAITING：设置指令已发出，但眼镜系统繁忙
/**
 * Notify glasses to turn off screen
 */
fun notifyScreenOff(){
    when(CxrApi.getInstance().notifyGlassScreenOff()){
        ValueUtil.CxrStatus.REQUEST_SUCCEED -> { // Notify screen off succeed
            Log.i(TAG, "notifyScreenOff succeed")
        }
        ValueUtil.CxrStatus.REQUEST_FAILED -> { // Notify screen off failed
            Log.e(TAG, "notifyScreenOff failed")
        }
        ValueUtil.CxrStatus.REQUEST_WAITING -> { // Notify screen off requested, but Glass is busy
            Log.e(TAG, "Notify Screen Off Command is in queue!")
        }
        else -> { // Unknown status
            Log.e(TAG, "Unknown status")
        }
    }
}
监听佩戴状态/镜腿开合状态/眼镜端TTS 播放状态
可以通过xrApi.getInstance().setGlassStatusUpdateListener(callback: GlassStatusUpdateListener)来设置眼镜佩戴状态/眼镜镜腿开合状态/眼镜端TTS 播放状态监听。

GlassStatusUpdateListener

fun onWearingStatusUpdated(wearingStatus: String?) 佩戴状态监听
wearingStatus “1”-佩戴状态，“0”-“未佩戴状态”
fun onGlassTempleStatusUpdated(templeStatus: String?) 镜腿开合状态监听
templeStatus “1”-镜腿打开状态，“0”-镜腿折叠状态
fun onGlassGlobalTtsStatusUpdated(ttsStatus: String?) 眼镜端TTS 播放状态监听
ttsStatus “1”-TTS 播报开始，“0”-TTS 播放结束
CxrApi.getInstance().setGlassStatusUpdateListener(object : GlassStatusUpdateListener{
    override fun onWearingStatusUpdated(wearingStatus: String?) {
        if (wearingStatus?.equals("1") ==  true){
            _wearingState.value = "Wearing"
        }else{
            _wearingState.value = "Not Wearing"
        }
    }

    override fun onGlassTempleStatusUpdated(templeStatus: String?) {
        if (templeStatus?.equals("1") ==  true){
            _templeStatus.value = "Temple Open"
        }else{
            _templeStatus.value = "Temple Close"
        }
    }

    override fun onGlassGlobalTtsStatusUpdated(ttsStatus: String?) {
        if (ttsStatus?.equals("1") ==  true){
            _ttsStatus.value = "TTS Start"
        }else if (ttsStatus?.equals("0") ==  true){
            _ttsStatus.value = "TTS End"
        }else{
            _ttsStatus.value = "TTS Unknown"
        }
    }
})

语音操作
在进行音频操作之前，请先完成设备连接。

Tips: 参考 CXRMSamples 示例工程中的 AudioUsageActivity.kt、AudioUsageViewModel.kt。

打开与监听眼镜端音频
1 眼镜端数据流监听
可以通过CxrApi.getInstance().setAudioStreamListener(cbk: AudioStreamListener?)接口来监听眼镜端的数据流。

AudioStreamListener接口中的fun onStartAudioStream(codeType: Int, streamType: String?)回调返回中监听到音频流的开始信号，fun onAudioStream(data: ByteArray?, offset: Int, size: Int)回调中获取到音频数据流。

AudioStreamListener

fun onStartAudioStream(codeType: Int, streamType: String?)：
codeType：音频数据编码类型。
1：pcm（常用：PCM 16KHz，16Bits，1 Channel）
2：ogg opus
3：agse pcm
4：agse ogg opus
5：rokid aec pcm
6：rokid aec ogg opus
streamType：自定义的音频流名称，用以区分不同的音频流数据
override fun onAudioStream(data: ByteArray?, offset: Int, size: Int)：
data：音频数据包
offset：音频数据在音频数据包中的起始位置
size：音频数据的有效长度
/** Listener for audio stream events from the CXR API */
private val audioListener = object : AudioStreamListener{
    /**
     * When the audio stream starts recording, this method will be called.
     * @param codeType Type of the audio stream: 1:PCM 16Bit 16KHz 1Channel
     * @param streamType Name of the audio stream
     */
    override fun onStartAudioStream(codeType: Int, streamType: String?) {
        // create a new file to record audio
        // ....other code when audio stream start
        // ....
        // ....other code end
    }
}
/**
 * This method will be called when audio recording data is available.
 * @param data audio data
 * @param offset Offset of the data
 * @param size Size of the data
 */
@SuppressLint("SdCardPath")
override fun onAudioStream(data: ByteArray?, offset: Int, size: Int) {
    val realBytes = if (size > 0) {
            data?.copyOfRange(offset, offset + size)
        } else {
            null
        }
    // ....other code is used to process the received audio data stream
    // ....
    // ....other code end

}
// Set audio data listener
CxrApi.getInstance().setAudioStreamListener(audioListener)
2 开始眼镜端录音
通过CxrApi.getInstance().openAudioRecord(codeType: Int, secondParam: Int, nameOfAudioStream: String): ValueUtil.CxrStatus方法开始录音。其中codeType为音频编码格式，目前仅推荐设置为 1（PCM，16KHz，16Bits，1 Channel）；secondParam 为模式参数（示例中传 1，具体取值见 SDK 或 CXRMSamples）；nameOfAudioStream为自定义的音频流名称，用以区分不同音频数据流。返回值是ValueUtil.CxrStatus类型的，用以判断是否请求成功。

TIPS:需要注意在调用openAudioRecord方法之前，要先调用setAudioStreamListener方法对音频流数据进行监听

CxrApi.getInstance().openAudioRecord(codeType: Int, secondParam: Int, nameOfAudioStream: String): ValueUtil.CxrStatus:开始录音

参数
codeType：音频编码格式，与上述 onStartAudioStream 的 codecType 一致（1–6）；目前仅推荐设置为 1，代表 PCM，16KHz，16Bits，1 Channel
secondParam：模式参数（示例中常用 1，具体见 SDK 或 CXRMSamples）
nameOfAudioStream：自定义的字段，用以区分不同的音频数据流
返回值
ValueUtil.CxrStatus.REQUEST_SUCCEED：设置成功
ValueUtil.CxrStatus.REQUEST_FAILED：设置失败
ValueUtil.CxrStatus.REQUEST_WAITING：设置指令已发出，但眼镜系统繁忙
/**
 * Start audio streaming.
 * This method initializes the audio recording process.
 */
fun startAudioStream(){
    // set audio stream listener first
    CxrApi.getInstance().setAudioStreamListener(audioListener)
    // start audio stream
    when(CxrApi.getInstance().openAudioRecord(1, 1, "audio_stream")){
        ValueUtil.CxrStatus.REQUEST_SUCCEED -> { // success
            // _recording.value = true
            Log.i(TAG, "startAudioStream: success")
        }
        ValueUtil.CxrStatus.REQUEST_FAILED -> { // failed
            // _recording.value = false
            Log.e(TAG, "startAudioStream: failed")
        }
        ValueUtil.CxrStatus.REQUEST_WAITING -> { // waiting
            // _recording.value = true
            Log.e(TAG, "startAudioStream: waiting")
        }
        else -> { // unknown
            // _recording.value = false
            Log.e(TAG, "startAudioStream: unknown")
        }
    }
}
3 停止眼镜端录音
通过调用CxrApi.getInstance().closeAudioRecord(nameOfAudioStream: String):ValueUtil.CxrStatus方法停止录音。其中参数中的nameOfAudioStream是在开始录音方法中自定义的音频流数据字段。返回值是ValueUtil.CxrStatus类型的，用以判断是否请求成功。

CxrApi.getInstance().closeAudioRecord(nameOfAudioStream: String):ValueUtil.CxrStatus: 停止录音

参数
nameOfAudioStream：自定义的字段，用以区分不同的音频数据流
返回
ValueUtil.CxrStatus.REQUEST_SUCCEED：设置成功
ValueUtil.CxrStatus.REQUEST_FAILED：设置失败
ValueUtil.CxrStatus.REQUEST_WAITING：设置指令已发出，但眼镜系统繁忙
/**
 * Stop audio streaming.
 * This method stops the audio recording process and removes the listener.
 */
fun stopAudioStream(){
    // stop audio stream
    when(CxrApi.getInstance().closeAudioRecord("audio_stream")){
        ValueUtil.CxrStatus.REQUEST_SUCCEED -> { // success
            Log.i(TAG, "stopAudioStream: success")
            // remove listener
            CxrApi.getInstance().setAudioStreamListener(null)
            // _recording.value = false
        }
        ValueUtil.CxrStatus.REQUEST_FAILED -> { // failed
            Log.e(TAG, "stopAudioStream: failed")
        }
        ValueUtil.CxrStatus.REQUEST_WAITING -> { // waiting
            Log.i(TAG,"stopAudioStream: waiting")
            // remove listener
            CxrApi.getInstance().setAudioStreamListener(null)
            // _recording.value = false
        }
        else -> { // unknown
            Log.e(TAG, "stopAudioStream: unknown")
        }
    }
}
设置拾音方式
Rokid 将控制眼镜端拾音模式的方法释放给了开发者。

可以通过fun changeAudioSceneId(audioSceneId: Int, audioSceneIdChanged: AudioSceneIdCallback):ValueUtil.CxrStatus方法来切换不同的拾音模式。其中audioSceneId是拾音模式。audioSceneIdChanged是AudioSceneIdCallback回调，这里需要注意的是，切换拾音模式是一个耗时操作，也就是说，切换拾音模式并不是瞬时生效的。返回值是ValueUtil.CxrStatus类型的，用以判断是否请求成功。

fun changeAudioSceneId(audioSceneId: Int, audioSceneIdChanged: AudioSceneIdCallback):ValueUtil.CxrStatus：设置拾音方式

参数
audioSceneId: 场景Id
0–近场拾音
1–远场拾音
2–全景拾音
AudioSceneIdCallback: 音频场景切换状态回调
fun onAudioSceneId(audioSceneId: Int, success: Boolean)
audioSceneId：场景ID
0–近场拾音
1–远场拾音
2–全景拾音
success：是否切换成功
true–切换成功
false–切换失败
返回
ValueUtil.CxrStatus.REQUEST_SUCCEED：设置成功
ValueUtil.CxrStatus.REQUEST_FAILED：设置失败
ValueUtil.CxrStatus.REQUEST_WAITING：设置指令已发出，但眼镜系统繁忙
/**
 * Enum representing different audio scene IDs.
 * NEAR: Near-field audio pickup
 * FAR: Far-field audio pickup
 * BOTH: Both near and far-field audio pickup
 * @param id The integer ID of the scene
 * @param sceneName The name of the scene
 */
enum class AudioSceneId(val id: Int, val sceneName: String){
    NEAR(0, "Near"),
    FAR(1, "Far"),
    BOTH(2, "Both")
}

/**
 * Change the audio scene ID.
 * @param sceneId The desired audio scene ID to switch to.
 * @see AudioSceneId
 * @see AudioSceneId.NEAR Near-field sound pickup
 * @see AudioSceneId.FAR Far-field sound pickup
 * @see AudioSceneId.BOTH full-scenario sound pickup
 */
fun changeAudioSceneId(sceneId: AudioSceneId){
    _changing.value = true
    // change audio scene
    val result = CxrApi.getInstance().changeAudioSceneId(sceneId.id){id, success ->
        _changing.value = false
        if (success){
            when (id) {
                0 -> {
                    _pickUpType.value = AudioSceneId.NEAR
                }
                1 -> {
                    _pickUpType.value = AudioSceneId.FAR
                }
                else -> {
                    _pickUpType.value = AudioSceneId.BOTH
                }
            }
        }
    }
    when(result){
        ValueUtil.CxrStatus.REQUEST_SUCCEED -> { // success
            Log.i(TAG, "changeAudioSceneId: success")
        }
        ValueUtil.CxrStatus.REQUEST_FAILED -> { // failed
            Log.e(TAG, "changeAudioSceneId: failed")
            _changing.value = false
        }
        ValueUtil.CxrStatus.REQUEST_WAITING -> { // waiting
            Log.i(TAG,"changeAudioSceneId: waiting")
        }
        else -> { // unknown
            Log.e(TAG, "changeAudioSceneId: unknown",)
            _changing.value = false
        }
    }
}
使用系统录音
开发者可以通过控制系统录音场景的方法CxrApi.getInstance().controlScene(sceneType: ValueUtil.CxrSceneType, toOpen: Boolean, args: String?): ValueUtil.CxrStatus，并将sceneType参数设置为ValueUtil.CxrSceneType.AUDIO_RECORD，通过控制toOpen参数控制开始或关闭系统录音。

系统录音结束之后，可以通过《媒体文件同步场景》来将录音文件同步到移动端。

CxrApi.getInstance().controlScene(sceneType: ValueUtil.CxrSceneType, toOpen: Boolean, args: String?): ValueUtil.CxrStatus 场景控制

参数
sceneType 场景类型
ValueUtil.CxrSceneType.AUDIO_RECORD 录音场景（当前章节仅介绍录音场景）
toOpen 开关控制
true 打开场景
false 关闭场景
返回
ValueUtil.CxrStatus.REQUEST_SUCCEED：设置成功
ValueUtil.CxrStatus.REQUEST_FAILED：设置失败
ValueUtil.CxrStatus.REQUEST_WAITING：设置指令已发出，但眼镜系统繁忙
/**
 * Control system audio record.
 * This method controls the system audio record scene.
 *
 * @param toOpen Indicates whether to open the scene (true) or close it (false).
 */
fun controlSystemAudioRecord(toOpen: Boolean){

    when(CxrApi.getInstance().controlScene(ValueUtil.CxrSceneType.AUDIO_RECORD, toOpen, null)){
        ValueUtil.CxrStatus.REQUEST_SUCCEED -> {
            Log.d(TAG, "Audio record started")
        }
        ValueUtil.CxrStatus.REQUEST_FAILED -> {
            Log.e(TAG, "Failed to start audio record")
        }
        ValueUtil.CxrStatus.REQUEST_WAITING -> {
            Log.e(TAG, "Requested but Glasses is not ready")
        }
        else -> {
            Log.e(TAG, "Unknown error")
        }
    }
}
还有一点，开发者可以通CxrApi.getInstance().sceneStatusInfo的返回值判断当前场景是否处于运行状态，并通过CxrApi.getInstance().setSceneStatusUpdateListener(sceneStatusUpdateListener: SceneStatusUpdateListener)设置场景监听，来监听场景变化。

sceneStatusInfo场景状态

SceneStatusInfo.isAudioRecordRunning 语音场景状态
true 语音场景运行中
false 语音场景未运行
CxrApi.getInstance().setSceneStatusUpdateListener(sceneStatusUpdateListener: SceneStatusUpdateListener) 设置场景状态监听

参数
sceneStatusUpdateListener 场景状态监听
fun onSceneStatusUpdated(sceneStatusInfo: SceneStatusInfo) 场景监听状态回调方法
SceneStatusInfo.isAudioRecordRunning 语音场景状态
true 语音场景运行中
false 语音场景未运行
这里以在init中获取语音场景的状态，并当语音场景处于开放状态的时候，将语音场景先关掉为例。

/**
 * Scene status update listener for audio recording.
 */
private val sceneStatusUpdateListener: SceneStatusUpdateListener =
    SceneStatusUpdateListener { sceneStatusInfo ->
        sceneStatusInfo?.isAudioRecordRunning?.let {
            // _systemAudioRecording.value = it
        }
    }

init {
    // get system audio recording status
    val isAudioSceneRunning = CxrApi.getInstance().sceneStatusInfo.isAudioRecordRunning
    _systemAudioRecording.value = isAudioSceneRunning
    // set scene status update listener
    CxrApi.getInstance().setSceneStatusUpdateListener(sceneStatusUpdateListener)
    if (isAudioSceneRunning){// if system audio recording is running, stop it
        controlSystemAudioRecord(false)
    }
}

照片操作
在进行拍照操作之前，请先完成设备连接。

Tips:参考示例代码中的：PictureActivity.kt 、PictureViewModel.kt

照片支持的大小
照片支持的大小参考下边的数组。需要注意的是宽高问题，因为眼镜端的摄像头相当于旋转了90°，所以列表中的Size.getWidth相当于实际成片的高，Size.getHeight相当于实际成片的宽。

/**
 * Array of available picture sizes for capturing images.
 * The camera on the Glasses has been rotated by 90°, so in this context,
 * the [Size.getWidth] from [androidx.compose.ui.geometry.Size] represents the actual image's height,
 * while [Size.getHeight] represents the actual image's width.
 */
val pictureSize: Array<Size> = arrayOf(
    Size(1920, 1080),
    Size(4032, 3024),
    Size(4000, 3000),
    Size(4032, 2268),
    Size(3264, 2448),
    Size(3200, 2400),
    Size(2268, 3024),
    Size(2876, 2156),
    Size(2688, 2016),
    Size(2582, 1936),
    Size(2400, 1800),
    Size(1800, 2400),
    Size(2560, 1440),
    Size(2400, 1350),
    Size(2048, 1536),
    Size(2016, 1512),
    Size(1600, 1200),
    Size(1440, 1080),
    Size(1280, 720),
    Size(720, 1280),
    Size(1024, 768),
    Size(800, 600),
    Size(648, 648),
    Size(854, 480),
    Size(800, 480),
    Size(640, 480),
    Size(480, 640),
    Size(352, 288),
    Size(320, 240),
    Size(320, 180),
    Size(176, 144)
)
拍照并获取图片
开发者可以通过CxrApi.getInstance().takeGlassPhotoGlobal(sizeWidth: Int, sizeHeight:Int, quality: Int, pictureCallback: PhotoResultCallback):ValueUtil.CxrStatus方法，在PhotoResultCallback回调的onPhotoResult方法中获取webP 格式的图片。

CxrApi.getInstance().takeGlassPhotoGlobal(sizeWidth: Int, sizeHeight:Int, quality: Int, pictureCallback: PhotoResultCallback):ValueUtil.CxrStatus: 获取图片方法

参数
sizeWidth: 支持列表中的Size.getWidth，实际成像的图片的高。
sizeHeight: 支持列表中的Size.getHeight，实际成像的图片的宽。
quality: 图片质量，范围【0-100】。
PhotoResultCallback，图片结果回调。
fun onPhotoResult(responseResult: ValueUtil.CxrStatus, data: ByteArray?) 结果回调
responseResult: 拍照结果
ValueUtil.CxrStatus.RESPONSE_SUCCEED: 拍照成功
其他: 拍照失败
data 图片数据，图片类型为webP 格式的。
返回值
ValueUtil.CxrStatus.REQUEST_SUCCEED：设置成功
ValueUtil.CxrStatus.REQUEST_FAILED：设置失败
ValueUtil.CxrStatus.REQUEST_WAITING：设置指令已发出，但眼镜系统繁忙
/** Callback for handling the result of photo capture */
private val pictureCallback = PhotoResultCallback { status, imageData ->
    _takingPhoto.value = false
    when (status) {
        ValueUtil.CxrStatus.RESPONSE_SUCCEED -> {
            // imageData is a webP format image
            // ....other code to use iamge data
            // ....
            // ....other code end

        }
        else -> {
            // Get Failed
        }
    }
}

/**
 * Capture a picture with the currently selected size.
 * Uses the CXR API to take a photo with the glass camera.
 */
fun takePicture() {
    _takingPhoto.value = true
    // Get the selected picture size
    val size = _selectedPictureSize.value
    // Take a photo with the selected size, because the camera on the Glasses has been rotated by 90°,
    // so the result image's height is the first param while the result image's width is the second param.
    // the third param is the quality of the image, the value range is [0,100], 100 means the best quality.
    // the fourth param is the callback for handling the result of photo capture.
    when(CxrApi.getInstance().takeGlassPhotoGlobal(size.width, size.height, 100, pictureCallback)){
        ValueUtil.CxrStatus.REQUEST_SUCCEED -> {
            // The photo capture request has been sent successfully.
        }
        ValueUtil.CxrStatus.REQUEST_FAILED -> {
            // The photo capture request has failed.
            //_takingPhoto.value = false
        }
        ValueUtil.CxrStatus.REQUEST_WAITING -> {
            // The photo capture request is waiting for the Glasses to be ready.
        }
        else -> {
            // The photo capture request has failed.
            //_takingPhoto.value = false
        }
    }
}
设置按键拍照参数
除了可以通过代码直接获取拍照数据的方法外，开发者还可以为通过眼镜拍照按键进行拍照设置成像参数。

通过CxrApi.getInstance().setPhotoParams(sizeWidht: Int, sizeHeight: Int):ValueUtil.CxrStatus设置全局拍照参数。

CxrApi.getInstance().setPhotoParams(sizeWidth: Int, sizeHeight: Int):ValueUtil.CxrStatus 拍照参数

参数
sizeWidth: 支持列表中的Size.getWidth，实际成像的图片的高。
sizeHeight: 支持列表中的Size.getHeight，实际成像的图片的宽。
返回值
ValueUtil.CxrStatus.REQUEST_SUCCEED：设置成功
ValueUtil.CxrStatus.REQUEST_FAILED：设置失败
ValueUtil.CxrStatus.REQUEST_WAITING：设置指令已发出，但眼镜系统繁忙
/**
 * Set the photo parameters for the camera based on selected size.
 * Configures the CXR API with the width and height of the selected picture size.
 */
fun setPhotoParams() {
    val result = CxrApi.getInstance()
        .setPhotoParams(_selectedPictureSize.value.width, _selectedPictureSize.value.height)
    when(result){
        ValueUtil.CxrStatus.REQUEST_SUCCEED -> {
            // The photo parameters have been set successfully.
        }
        ValueUtil.CxrStatus.REQUEST_FAILED -> {
            // The photo parameters have failed to be set.
        }
        ValueUtil.CxrStatus.REQUEST_WAITING -> {
            // The photo parameters are waiting for the Glasses to be ready.
        }
        else -> {
            // The photo parameters have failed to be set.
        }
    }
}
录像操作
在进行录像操作之前，请先完成设备连接。

Tips:参考示例代码中的：VideoActivity.kt 、VideoViewModel.kt

使用系统录像
开发者可以通过控制系统场景场景的方法CxrApi.getInstance().controlScene(sceneType: ValueUtil.CxrSceneType, toOpen: Boolean, args: String?): ValueUtil.CxrStatus，并将sceneType参数设置为ValueUtil.CxrSceneType.VIDEO_RECORD，通过控制toOpen参数控制开始或关闭系统录像。

系统录像结束之后，可以通过《媒体文件同步》来将录像文件同步到移动端。

设置录像参数（可选）
在开始录像前，可通过 CxrApi.getInstance().setVideoParams(duration: Int, frameRate: Int, width: Int, height: Int, timeUnit: Int): ValueUtil.CxrStatus 设置录像时长、帧率、分辨率与时长单位。其中 timeUnit 为 0 表示时长为分钟，为 1 表示时长为秒。分辨率需使用支持的尺寸列表（与照片/录像支持列表一致）。参考示例 VideoViewModel.kt 中的 setVideoParams 与 videoSize。

使用系统录像
CxrApi.getInstance().controlScene(sceneType: ValueUtil.CxrSceneType, toOpen: Boolean, args: String?): ValueUtil.CxrStatus 场景控制

参数
sceneType 场景类型
ValueUtil.CxrSceneType.VIDEO_RECORD 录像场景（当前章节仅介绍录像场景）
toOpen 开关控制
true 打开场景
false 关闭场景
返回
ValueUtil.CxrStatus.REQUEST_SUCCEED：设置成功
ValueUtil.CxrStatus.REQUEST_FAILED：设置失败
ValueUtil.CxrStatus.REQUEST_WAITING：设置指令已发出，但眼镜系统繁忙
/**
 * Control video record.
 * This method controls the video record scene.
 *
 * @param toOpen Indicates whether to open the scene (true) or close it (false).
 */
fun controlVideoScene(toOpen: Boolean){

    when(CxrApi.getInstance().controlScene(ValueUtil.CxrSceneType.VIDEO_RECORD, toOpen, null)){
        ValueUtil.CxrStatus.REQUEST_SUCCEED -> {
            Log.d(TAG, "Video record started")
        }
        ValueUtil.CxrStatus.REQUEST_FAILED -> {
            Log.e(TAG, "Failed to start video record")
        }
        ValueUtil.CxrStatus.REQUEST_WAITING -> {
            Log.e(TAG, "Requested but Glasses is not ready")
        }
        else -> {
            Log.e(TAG, "Unknown error")
        }
    }
}
还有一点，开发者可以通CxrApi.getInstance().sceneStatusInfo的返回值判断当前场景是否处于运行状态，并通过CxrApi.getInstance().setSceneStatusUpdateListener(sceneStatusUpdateListener: SceneStatusUpdateListener)设置场景监听，来监听场景变化。

sceneStatusInfo场景状态

SceneStatusInfo.isVideoRecordRunning 录像场景状态
true 录像场景运行中
false 录像场景未运行
CxrApi.getInstance().setSceneStatusUpdateListener(sceneStatusUpdateListener: SceneStatusUpdateListener) 设置场景状态监听

参数
sceneStatusUpdateListener 场景状态监听
fun onSceneStatusUpdated(sceneStatusInfo: SceneStatusInfo) 场景监听状态回调方法
SceneStatusInfo.isVideoRecordRunning 录像场景状态
true 录像场景运行中
false 录像场景未运行
这里以在init中获取录像场景的状态，并当录像场景处于开放状态的时候，将录像场景先关掉为例。

/**
 * Listener for scene status updates
 * Updates the recording status when notified by the CXR system
 */
private val sceneStatusUpdateListener = SceneStatusUpdateListener { p0 ->
    p0?.isVideoRecordRunning?.let {
        _isRecording.value = it
    }
}

init {
    // Initialize recording status
    val sceneStatus = CxrApi.getInstance().sceneStatusInfo.isVideoRecordRunning
    _isRecording.value = sceneStatus
    // Set the scene status listener
    CxrApi.getInstance().setSceneStatusUpdateListener(sceneStatusUpdateListener)
    // Stop recording if it's already running
    if (sceneStatus){
        controlVideoScene(false)
    }
}
/**
 * Clean up resources when the ViewModel is cleared
 * Stops recording if it's currently running
 * Unsets the scene status listener
 */
override fun onCleared() {
    if (_isRecording.value){
        controlVideoScene(false)
    }
    CxrApi.getInstance().setSceneStatusUpdateListener(null)
    super.onCleared()
}

在进行实时取流之前，请先完成设备连接。

Tips:参考示例代码中的：LiveVideoActivity.kt、LiveVideoViewModel.kt、LiveVideoFrameBuffer.kt、LiveVideoMp4Recorder.kt、LiveVideoPreviewRenderer.kt

概述
实时取流用于获取眼镜端摄像头的实时视频流，可用于本地预览或录制成文件。使用前需确保设备已通过蓝牙连接；若眼镜端相机正被其他场景占用，需先关闭该场景再打开实时取流。

检查相机是否被占用
可通过 CxrApi.getInstance().isGlassCameraInUse 判断眼镜端相机是否正被其他场景使用。为 true 时无法打开实时取流，需先关闭占用相机的场景。

val inUse = CxrApi.getInstance().isGlassCameraInUse
if (inUse) {
    // 相机被占用，无法打开实时取流
    return
}
分辨率与编码
支持分辨率：示例中使用的分辨率为 640×480、800×600（以 SDK 实际支持为准）。
编码格式：openCameraVideo 的 videoEncoderMode 参数：1 表示 H264，2 表示 H265。
打开实时取流
设置监听：先通过 CxrApi.getInstance().setMediaStreamListener(mediaStreamListener: MediaStreamListener?) 设置媒体流监听，再调用打开接口，否则无法收到帧数据。
打开相机流：调用 CxrApi.getInstance().openCameraVideo(width: Int, height: Int, frameRotate: Int, videoEncoderMode: Int): ValueUtil.CxrStatus。
width、height：分辨率宽高。
frameRotate：帧旋转（示例中传 0）。
videoEncoderMode：1 为 H264，2 为 H265。
返回值：REQUEST_SUCCEED 请求成功，REQUEST_WAITING 已排队，REQUEST_FAILED 请求失败。
// 先设置监听，再打开
CxrApi.getInstance().setMediaStreamListener(mediaStreamListener)
val width = 640
val height = 480
val frameRotate = 0
val videoEncoderMode = 1  // 1=H264, 2=H265
val status = CxrApi.getInstance().openCameraVideo(width, height, frameRotate, videoEncoderMode)
when (status) {
    ValueUtil.CxrStatus.REQUEST_SUCCEED, ValueUtil.CxrStatus.REQUEST_WAITING -> { /* 已请求，等待 onCameraOpened */ }
    ValueUtil.CxrStatus.REQUEST_FAILED -> { CxrApi.getInstance().setMediaStreamListener(null) }
    else -> { CxrApi.getInstance().setMediaStreamListener(null) }
}
MediaStreamListener 回调
onCameraOpened()：相机已打开，可开始接收帧。
onCameraClosed()：相机已关闭。
onCameraError()：打开或取流过程中出错。
onCameraFrame(data: ByteArray?, timestamp: Long)：收到一帧数据；data 为编码后的帧数据，timestamp 为时间戳。回调可能在工作线程，如需更新 UI 请切到主线程。
private val mediaStreamListener = object : MediaStreamListener {
    override fun onCameraOpened() { /* 可开始接收帧，可更新 UI */ }
    override fun onCameraClosed() { }
    override fun onCameraError() { /* 出错处理 */ }
    override fun onCameraFrame(data: ByteArray?, timestamp: Long) {
        if (data == null) return
        // 送入预览或录制；若更新 UI 需切到主线程
        // previewRenderer?.feedFrame(data, timestamp)
        // mp4Recorder?.writeFrame(data, timestamp)
    }
}
关闭实时取流
调用 CxrApi.getInstance().closeCameraVideo(): ValueUtil.CxrStatus 关闭相机流。关闭后建议将 setMediaStreamListener(null) 移除监听。

CxrApi.getInstance().closeCameraVideo()
CxrApi.getInstance().setMediaStreamListener(null)
可选：录制成 MP4
在收到 onCameraFrame 时，可将 data 与 timestamp 送入本地编码器（如 MediaCodec）编码为 MP4。示例中通过 LiveVideoMp4Recorder 在开启“录制到文件”时将帧写入 MP4，流程见 LiveVideoViewModel 与 LiveVideoMp4Recorder.kt。

// 在 onCameraFrame 内，将 data、timestamp 交给录制器写入
// recorder.writeFrame(data, timestamp)
// 完整实现见 CXRMSamples LiveVideoMp4Recorder.kt
参考示例
LiveVideoActivity.kt、LiveVideoViewModel.kt：打开/关闭取流、监听与状态。
LiveVideoFrameBuffer.kt：帧缓冲与插帧（可选）。
LiveVideoPreviewRenderer.kt：本地 Surface 预览。
LiveVideoMp4Recorder.kt：录制成 MP4（可选）。

媒体文件同步
在进行媒体文件操作之前，请先完成设备连接。

Tips:参考示例代码中的：MediaFileActivity.kt 、MediaFileViewModel.kt

媒体文件的管理依赖Wi-Fi 网络提供的大带宽。

权限与连接
申请 Wi‑Fi P2P 权限（API 33+）
在 AndroidManifest.xml 中添加：

<uses-permission android:name="android.permission.NEARBY_WIFI_DEVICES" />
运行时可按需调用：

requestPermissions(arrayOf(android.Manifest.permission.NEARBY_WIFI_DEVICES), 1024)
Wi‑Fi P2P 连接两种实现方案
媒体文件同步的 Wi‑Fi 连接与同步流程提供两种实现方案，开发者可按需选择。两种方案均需在使用完后调用 deinitWifiP2P() 断开连接；Wi‑Fi 操作耗能较大，用毕请及时断开。

方案一：initWifiP2P + startSync
通过 SDK 直接建立 P2P 连接并同步：使用 initWifiP2P 连接眼镜端 P2P Group，连接成功后使用 startSync 同步文件。

fun CxrApi.getInstance().initWifiP2P(wifiP2PStatusCallback: WifiP2PStatusCallback): ValueUtil.CxrStatus Wi-Fi P2P 初始化连接方法

参数

wifiP2PStatusCallback 连接状态接口
fun onConnected() 连接成功回调方法
fun onDisconnected() 连接断开回调方法
fun onFailed(errorCode: ValueUtil.CxrWifiErrorCode?) 连接失败回调方法
errorCode 失败原因
ValueUtil.CxrWifiErrorCode.WIFI_DISABLED Wi-Fi 不可用
ValueUtil.CxrWifiErrorCode.WIFI_CONNECT_FAILED Wi-Fi 连接失败
ValueUtil.CxrWifiErrorCode.UNKNOWN 未知原因
返回值

ValueUtil.CxrStatus.REQUEST_SUCCEED：设置成功
ValueUtil.CxrStatus.REQUEST_FAILED：设置失败
ValueUtil.CxrStatus.REQUEST_WAITING：设置指令已发出，但眼镜系统繁忙
在使用完后，使用 CxrApi.getInstance().deinitWifiP2P() 方法断开网络连接。

/**
 * 方案一：通过 initWifiP2P 连接眼镜端 P2P
 */
fun connect(){
    when (CxrApi.getInstance().initWifiP2P( object : WifiP2PStatusCallback {

        /**
         * Called when connection is established
         */
        override fun onConnected() {
            // code when Wi-Fi P2P connected
            // ....
            // code end
        }

        /**
         * Called when device is disconnected
         */
        override fun onDisconnected() {
            // code when Wi-Fi P2P disconnected
            // ....
            // code end
        }

        /**
         * Called when connection attempt fails
         * @param errorCode The error code indicating failure reason
         */
        override fun onFailed(errorCode: ValueUtil.CxrWifiErrorCode?) {
            // code when Wi-Fi P2P connect failed
            // ....
            // code end
        }
    })) {
        ValueUtil.CxrStatus.REQUEST_SUCCEED -> {// request succeed
            
        }
        ValueUtil.CxrStatus.REQUEST_FAILED -> {// request failed
            
        }
        ValueUtil.CxrStatus.REQUEST_WAITING -> {// requested but Glasses is busy
            
        }
        else -> {// unknown
            
        }
    }
}

fun disconnect(){
    CxrApi.getInstance().deinitWifiP2P()
}
方案二：initWifiP2P2 + startSync2
由 SDK 提供 P2P 设备信息，应用层自行完成 P2P 连接并取得 IP 后，使用 startSync2 传入该 IP 进行同步。适用于需要自行控制 P2P 发现与连接流程的场景。

CxrApi.getInstance().initWifiP2P2(booleanParam: Boolean, wifiP2PStatusCallback: WifiP2PStatusCallback): ValueUtil.CxrStatus 方案二 Wi‑Fi P2P 初始化

参数
booleanParam 与 wifiP2PStatusCallback 的用法见示例；回调中除 onConnected、onDisconnected、onFailed 外，还包含 onP2pDeviceAvailable(name: String?, macAddress: String?, deviceType: String?)，用于获取眼镜端 P2P 设备信息，便于应用层发起 P2P 连接并获取 IP。
返回值：同上（REQUEST_SUCCEED / REQUEST_FAILED / REQUEST_WAITING）
连接建立并取得眼镜端 IP 后，可调用 CxrApi.getInstance().startSync2(savePath: String, mediaTypes: Array<ValueUtil.CxrMediaType>, ipAddress: String, syncStatusCallback: SyncStatusCallback): Boolean 开始同步，其中 ipAddress 为方案二中应用层 P2P 连接得到的眼镜端 IP。其他参数与 startSync 一致。停止同步仍使用 stopSync()，断开连接仍使用 deinitWifiP2P()。

参考示例：MediaFileViewModel.kt 中方案二使用 initWifiP2P2、onP2pDeviceAvailable 与 P2PUtils 建立连接后调用 startSync2(path, mediaTypes, ipAddress, syncStatus)。

监听媒体数量变化
可以通过CxrApi.getInstance().setMediaFilesUpdateListener(mediaFilesUpdateListener: MediaFilesUpdateListener?) 方法设置媒体数量变化的监听。这里普遍的做法是在监听到媒体文件数量变化后获取一下未同步文件数量（下一个章节介绍）。

CxrApi.getInstance().setMediaFilesUpdateListener(mediaFilesUpdateListener: MediaFilesUpdateListener?) 媒体文件数量变化监听设置方法

参数
mediaFilesUpdateListener 媒体文件数量变化监听回调接口，如果是null 则表示移除监听
fun onMediaFilesUpdated() 媒体文件更新回调方法
/** Listener for media file updates */
private val mediaFilesUpdateListener = MediaFilesUpdateListener { 
    // get unsync files number when files is update
    getUnsyncNum() 
}

init {
    CxrApi.getInstance().setMediaFilesUpdateListener(mediaFilesUpdateListener)
}

/**
 * Called when the ViewModel is cleared to clean up resources
 */
override fun onCleared() {
	
    CxrApi.getInstance().setMediaFilesUpdateListener(null)
    super.onCleared()
}
获取未同步文件数量
可以通过CxrApi.getInstance().getUnsyncNum(callback: UnsyncNumResultCallback): ValueUtil.CxrStatus获取未同步文件数量。

CxrApi.getInstance().getUnsyncNum(unsyncNumResultCallback: UnsyncNumResultCallback): ValueUtil.CxrStatus 获取未同步文件数量

参数
unsyncNumResultCallback 未同步文件回调接口
fun onUnsyncNumResult(status: ValueUtil.CxrStatus, audioNum: Int, pictureNum: Int, videoNum: Int) 未同步文件数量回调方法
status 返回状态
ValueUtil.CxrStatus.RESPONSE_SUCCEED 返回成功
ValueUtil.CxrStatus.RESPONSE_INVALID 返回无效
ValueUtil.CxrStatus.RESPONSE_TIMEOUT 超时
audioNum 录音文件数量
pictureNum 照片文件数量
videoNum 录像文件数量
返回值
ValueUtil.CxrStatus.REQUEST_SUCCEED：设置成功
ValueUtil.CxrStatus.REQUEST_FAILED：设置失败
ValueUtil.CxrStatus.REQUEST_WAITING：设置指令已发出，但眼镜系统繁忙
/**
 * Callback for handling the result of getting unsynchronized media file counts
 */
private val unsyncNumResultCallback =
    UnsyncNumResultCallback { status, audioNum, pictureNum, videoNum ->
        when(status){
            ValueUtil.CxrStatus.RESPONSE_SUCCEED -> {
                Log.i(tag, "get unsync num succeed")
                _audioNumber.value = audioNum
                _pictureNumber.value = pictureNum
                _videoNumber.value = videoNum
            }
            ValueUtil.CxrStatus.RESPONSE_INVALID -> {
                Log.e(tag, "get unsync num failed: RESPONSE_INVALID")
            }
            ValueUtil.CxrStatus.RESPONSE_TIMEOUT -> {
                Log.e(tag, "get unsync num failed: RESPONSE_TIMEOUT")
            }
            else -> {
                Log.e(tag, "get unsync num failed: unknown error")
            }
        }    
/**
 * Retrieves the number of unsynchronized media files
 */
fun getUnsyncNum(){
    when(CxrApi.getInstance().getUnsyncNum(unsyncNumResultCallback)){
        ValueUtil.CxrStatus.REQUEST_SUCCEED -> {// request succeed
        }
        ValueUtil.CxrStatus.REQUEST_FAILED -> {
            Log.e(tag, "get unsync num failed")
        }
        ValueUtil.CxrStatus.REQUEST_WAITING -> {
            // Glasses busy
            Log.e(tag, "get unsync num failed: REQUEST_WAITING")
        }
        else -> {
            Log.e(tag, "get unsync num unknown status")
        }
    }
}
开始 / 停止同步
方案一使用 CxrApi.getInstance().startSync(savePath, mediaTypes, syncStatusCallback) 同步所有媒体文件。方案二在取得 P2P 连接的 IP 后使用 startSync2(savePath, mediaTypes, ipAddress, syncStatusCallback)，见上文方案二说明。

以下为方案一接口说明。单个文件同步两种方案均可使用 syncSingleFile。

fun CxrApi.getInstance().startSync(savePath: String, mediaTypes: Array<ValueUtil.CxrMediaType>, syncStatusCallback: SyncStatusCallback): Boolean 方案一同步所有文件

参数

savePath 文件存储目标位置
mediaTypes 需要同步的文件类型列表
CxrMediaType.AUDIO 音频类型
CxrMediaType.PICTURE 图片类型
CxrMediaType.VIDEO 视频类型
CxrMediaType.ALL 所有文件
syncStatusCallback 同步回调接口
fun onSyncStart() 文件同步开始接口方法
fun onSingleFileSynced(fileName: String) 单个文件同步接口方法
fileName 同步文件名
fun onSyncFailed() 同步失败接口方法
fun onSyncFinished() 同步结束接口方法
返回值

true 调用成功
false 调用失败
fun CxrApi.getInstance().syncSingleFile(savePath: String, mediaType: ValueUtil.CxrMediaType, filePath: String, syncStatusCallback: SyncStatusCallback): Boolean 同步单个文件方法

参数

savePath 文件存储目标位置
mediaType 需要同步的文件类型，同上
filePath 眼镜端文件存储路径
syncStatusCallback 同步回调接口，同上。
返回值

true 调用成功

false 调用失败

/**
 * Callback for monitoring synchronization status
 */
private val syncStatus = object : SyncStatusCallback{
    /**
     * Called when synchronization starts
     */
    override fun onSyncStart() {
        // Todo when sync start
    }

    /**
     * Called when a single file has been synchronized
     * @param filename The name of the synchronized file
     */
    override fun onSingleFileSynced(filename: String?) {
        Log.i(tag, "sync single file, name = $filename")
		// ....other code start
        // ....code when sync single file
        // ....other code end
    }

    /**
     * Called when synchronization fails
     */
    override fun onSyncFailed() {
        Log.e(tag, "sync failed")
        // ....other code start
        // ....code when sync failed
        // ....other code end
    }

    /**
     * Called when synchronization finishes successfully
     */
    override fun onSyncFinished() {
        // ....other code start
        // ....code when sync finished
        // ....other code end
    }

}
/**
 * Starts synchronization of media files
 * @param mediaTypes Array of media types to synchronize
 */
@SuppressLint("SdCardPath")
fun startSync(mediaTypes: Array<ValueUtil.CxrMediaType>){

    when(CxrApi.getInstance().startSync("/sdcard/Download/Rokid/Media/", mediaTypes, syncStatus)){
        true -> {
            Log.i(tag, "start sync succeed")
        }
        false -> {
            Log.e(tag, "start sync failed")
        }
    }
}

/**
 * Starts synchronization of a single media file
 * @param filePath Path to the file to synchronize
 * @param mediaType Media type of the file to synchronize
 */
fun startSyncSingle(filePath: String, mediaType: ValueUtil.CxrMediaType){
    CxrApi.getInstance().syncSingleFile("/sdcard/Download/Rokid/Media/", mediaType,  "/sdcard/Download/Rokid/Media/test.jpg", syncStatus)
}
可以调用fun CxrApi.getInstance().stopSync() 方法停止文件同步。

/**
 * Stops the ongoing synchronization process
 */
fun stopSync(){
    CxrApi.getInstance().stopSync()
}

TTS和通知、Toast
在进行媒体文件操作之前，请先完成设备连接。

Tips:参考示例代码中的：TTSAndNotificationActivity.kt 、TTSAndNotificationViewModel.kt

向眼镜端发送通知
可以通过fun CxrApi.getInstance().sendGlobalMsgContent(iconType: Int, content: String, playTTS: Boolean): ValueUtil.CxrStatus 将通知发送到眼镜端。

fun CxrApi.getInstance().sendGlobalMsgContent(iconType: Int, content: String, playTTS: Boolean): ValueUtil.CxrStatus 通知发送方法

参数
iconType 图标类型
0 没有图标
1 Info 或者错误图标
2 成功图标
3 加载中图标
4 未知图标
content 通知内容
playTTS 是否用眼镜端TTS 进行播报
返回值
ValueUtil.CxrStatus.REQUEST_SUCCEED：设置成功
ValueUtil.CxrStatus.REQUEST_FAILED：设置失败
ValueUtil.CxrStatus.REQUEST_WAITING：设置指令已发出，但眼镜系统繁忙
/**
 * The icon type of the notification.
 */
enum class IconType(val iconType: Int) {
    NONE(0), // Info
    InfoOrError(1), // Warning
    Success(2), // Error
    During(3), // Success
    Unknown(4), // Unknown
}
/**
 * Send a notification to the Glasses.
 *
 * @param iconType The icon type of the notification.
 * @param content The content of the notification.
 * @param playTTS Whether to play TTS.
 */
fun sendNotification(iconType: IconType, content: String, playTTS: Boolean) {
    when(CxrApi.getInstance().sendGlobalMsgContent(iconType.iconType, content, playTTS)){
        ValueUtil.CxrStatus.REQUEST_SUCCEED -> {
            // The notification has been sent successfully.
        }
        ValueUtil.CxrStatus.REQUEST_FAILED -> {
            // The notification has failed to be sent.
        }
        ValueUtil.CxrStatus.REQUEST_WAITING -> {
            // The notification is waiting for the Glasses to be ready.
        }
        else -> {
            // The notification has failed to be sent.
        }
    }
}
向眼镜端发送Toast
可以通过fun CxrApi.getInstance().sendGlobalToastContent(iconType: Int, content: String, playTTS: Boolean): ValueUtil.CxrStatus 将通知发送到眼镜端。

fun CxrApi.getInstance().sendGlobalToastContent(iconType: Int, content: String, playTTS: Boolean): ValueUtil.CxrStatus Toast 发送方法

参数
iconType 图标类型
0 没有图标
1 Info 或者错误图标
2 成功图标
3 加载中图标
4 未知图标
content Toast 内容
playTTS 是否用眼镜端TTS 进行播报
返回值
ValueUtil.CxrStatus.REQUEST_SUCCEED：设置成功
ValueUtil.CxrStatus.REQUEST_FAILED：设置失败
ValueUtil.CxrStatus.REQUEST_WAITING：设置指令已发出，但眼镜系统繁忙
/**
 * The icon type of the notification.
 */
enum class IconType(val iconType: Int) {
    NONE(0), // Info
    InfoOrError(1), // Warning
    Success(2), // Error
    During(3), // Success
    Unknown(4), // Unknown
}
/**
 * Send a toast to the Glasses.
 *
 * @param iconType The icon type of the toast.
 * @param content The content of the toast.
 * @param playTTS Whether to play TTS.
 */
fun sendToast(iconType: IconType, content: String, playTTS: Boolean) {
    when(CxrApi.getInstance().sendGlobalToastContent(iconType.iconType, content, playTTS)){
        ValueUtil.CxrStatus.REQUEST_SUCCEED -> {
            // The toast has been sent successfully.
        }
        ValueUtil.CxrStatus.REQUEST_FAILED -> {
            // The toast has failed to be sent.
        }
        ValueUtil.CxrStatus.REQUEST_WAITING -> {
            // The toast is waiting for the
        }
        else -> {
            // The toast has failed to be sent.
        }
    }
}
直接在眼镜端播放TTS
可以通过fun CxrApi.getInstance().sendGlobalTtsContent( content: String): ValueUtil.CxrStatus 将通知发送到眼镜端。

fun CxrApi.getInstance().sendGlobalTtsContent(content: String): ValueUtil.CxrStatus 通知发送方法

参数
content TTS 内容
返回值
ValueUtil.CxrStatus.REQUEST_SUCCEED：设置成功
ValueUtil.CxrStatus.REQUEST_FAILED：设置失败
ValueUtil.CxrStatus.REQUEST_WAITING：设置指令已发出，但眼镜系统繁忙
/**
 * Send a TTS to the Glasses.
 *
 * @param content The content of the TTS.
 */
fun tts(content: String) {
    when(CxrApi.getInstance().sendGlobalTtsContent(content)){
        ValueUtil.CxrStatus.REQUEST_SUCCEED -> {
            // The TTS has been sent successfully.
        }
        ValueUtil.CxrStatus.REQUEST_FAILED -> {
            // The TTS has failed to be sent.
        }
        ValueUtil.CxrStatus.REQUEST_WAITING -> {
            // The TTS is waiting for the Glasses to be ready.
        }
        else -> {
            // The TTS has failed to be sent.
        }
    }
}
TTS 参数设置
眼镜端TTS 支持参数设置，包括可以通过fun CxrApi.getInstance().setLocalTtsParam(voiceType: String): ValueUtil.CxrStatus 设置音色，以及通过fun CxrApi.getInstance().setLocalTtsSpeed(speed: Float): ValueUtil.CxrStatus 设置TTS 速度。

fun CxrApi.getInstance().setLocalTtsParam(voiceType: String): ValueUtil.CxrStatus 设置TTS 参数

参数
voiceType 音色参数
"{\"voice_id\": 1}" 女声
"{\"voice_id\": 2}" 男声
返回值
ValueUtil.CxrStatus.REQUEST_SUCCEED：设置成功
ValueUtil.CxrStatus.REQUEST_FAILED：设置失败
ValueUtil.CxrStatus.REQUEST_WAITING：设置指令已发出，但眼镜系统繁忙
/**
 * The voice type of the TTS.
 */
enum class VoiceType(val voiceType: String) {
    Girl("{\"voice_id\": 1}"), // Girl
    Boy("{\"voice_id\": 2}"), // Boy

}
/**
 * Set the local TTS parameter.
 *
 * @param voiceType The voice type of the TTS.
 */
fun setTTSLocalParam(voiceType: VoiceType){
    when(CxrApi.getInstance().setLocalTtsParam(voiceType.voiceType)){
        ValueUtil.CxrStatus.REQUEST_SUCCEED -> {
            // The TTS parameter has been set successfully.
        }
        ValueUtil.CxrStatus.REQUEST_FAILED -> {
            // The TTS parameter has failed to be set.
        }
        ValueUtil.CxrStatus.REQUEST_WAITING -> {
            // The TTS parameter is waiting for the Glasses to be ready.
        }
        else -> {
            // The TTS parameter has failed to be set.
        }
    }
}
fun CxrApi.getInstance().setLocalTtsSpeed(speed: Float): ValueUtil.CxrStatus 设置TTS 播放速度

参数
speed 播放速度，参考范围是[0.75, 4.0]
返回值

ValueUtil.CxrStatus.REQUEST_SUCCEED：设置成功

ValueUtil.CxrStatus.REQUEST_FAILED：设置失败

ValueUtil.CxrStatus.REQUEST_WAITING：设置指令已发出，但眼镜系统繁忙
/**
 * Set the TTS speed.
 *
 * @param speed The speed of the TTS. The range is 0.75 to 4.0.
 */
fun setTTSSpeed(@FloatRange(0.75, 4.0) speed: Float){

    when(CxrApi.getInstance().setLocalTtsSpeed(speed)){
        ValueUtil.CxrStatus.REQUEST_SUCCEED -> {
            // The TTS speed has been set successfully.
        }
        ValueUtil.CxrStatus.REQUEST_FAILED -> {
            // The TTS speed has failed to be set.
        }
        ValueUtil.CxrStatus.REQUEST_WAITING -> {
            // The TTS speed is waiting for the Glasses to be ready.
        }
        else -> {
            // The TTS speed has failed to be set.
        }
    }
}

自定义View
在进行自定义页面操作之前，请先完成设备连接。

Tips:参考示例代码中的：CustomViewActivity.kt、CustomViewViewModel.kt、SelfViewJson.kt、LinearLayoutProps.kt、RelativeLayoutProps.kt、TextViewProps.kt、ImageViewProps.kt、UpdateViewJson.kt

概述
自定义页面用于在眼镜端显示由移动端定义的 UI 布局（如 LinearLayout、RelativeLayout、TextView、ImageView、com.airbnb.lottie.LottieAnimationView 等），并通过 JSON 描述结构与属性，支持打开、更新、关闭、图标上传以及 Lottie 动画展示与控制。布局控件支持嵌套。

支持控件
LinearLayout	id	[id]
layout_width
layout_height	match_parent
wrap_content
[value]dp
layout_gravity(子控件用)
gravity	start
top
end
bottom
center
center_horizontal
center_vertical
orientation	vertical
horizontal
marginStart
marginTop
marginEnd
marginBottom	[value]dp
layout_weight(子控件用)	[value]
paddingStart
paddingTop
paddingEnd
paddingBottom	[value]dp
backgroundColor	#FF000000
RelativeLayout	id	[id]
layout_width
layout_height	match_parent
wrap_content
[value]dp
paddingStart
paddingTop
paddingEnd
paddingBottom	[value]dp
backgroundColor	#FF000000
marginStart
marginTop
marginEnd
marginBottom	[value]dp
layout_toStartOf(子控件用)
layout_above(子控件用)
layout_toEndOf(子控件用)
layout_below(子控件用)
layout_alignBaseline(子控件用)
layout_alignStart(子控件用)
layout_alignTop(子控件用)
layout_alignEnd(子控件用)
layout_alignBottom(子控件用)	[id]
layout_alignParentStart(子控件用)
layout_alignParentTop(子控件用)
layout_alignParentEnd(子控件用)
layout_alignParentBottom(子控件用)
layout_centerInParent(子控件用)
layout_centerHorizontal(子控件用)
layout_centerVertical(子控件用)	true
false
TextView	id	[id]
layout_width
layout_height	match_parent
wrap_content
[value]dp
text	[text]
textColor	#FF00FF00
textSize	[value]sp
gravity	start
top
end
bottom
center
center_horizontal
center_vertical
textStyle	bold
italic
paddingStart
paddingTop
paddingEnd
paddingBottom	[value]dp
marginStart
marginTop
marginEnd
marginBottom	[value]dp
ImageView	id	[id]
layout_width
layout_height	match_parent
wrap_content
[value]dp
name	[icon_name]
scaleType	matrix
fix_xy
fix_start
fix_center
fix_end
center
center_crop
center_inside
com.airbnb.lottie.LottieAnimationView	id	[id]
layout_width
layout_height	match_parent
wrap_content
[value]dp
app:lottie_autoPlay	true
false
app:lottie_loop	
app:lottie_repeatCount	[value]
app:lottie_repeatMode	restart
reverse
app:lottie_scale	[value]
app:lottie_speed	[value]
app:lottie_progress	[value]
设置监听
通过 CxrApi.getInstance().setCustomViewListener(listener: CustomViewListener?) 设置或移除自定义页面状态监听。CustomViewListener 回调包括：onOpened() 页面已打开、onOpenFailed(p0: Int) 打开失败、onUpdated() 更新完成、onClosed() 页面已关闭、onIconsSent() 图标已发送完成。传入 null 可移除监听。

private val customViewListener = object : CustomViewListener {
    override fun onIconsSent() { }
    override fun onOpened() { /* 页面已打开 */ }
    override fun onOpenFailed(p0: Int) { /* -1 表示禁止打开(如 OTA/CALL) */ }
    override fun onUpdated() { }
    override fun onClosed() { }
}
CxrApi.getInstance().setCustomViewListener(customViewListener)
// 取消监听
CxrApi.getInstance().setCustomViewListener(null)
上传资源（图标与 Lottie 动画）
如果自定义页面中包含 ImageView 图标或 Lottie 动画资源，必须在调用 openCustomView 之前完成资源上传。否则打开自定义页面时将无法正确显示对应的图片或动画。

其中，图标上传使用 CxrApi.getInstance().sendCustomViewIcons(icons: List<IconInfo>) 完成，其中 IconInfo 包含名称与 Base64 编码的图片数据。发送完成后可通过 CustomViewListener.onIconsSent() 得知。参考示例工程中的 CustomViewViewModel.uploadIcon 与 sendCustomViewIcons 的用法。

val icon1Info = IconInfo("icon1", icon1Base64)  // icon1Base64 为图片 Base64 字符串
val vectorInfo = IconInfo("vector", vectorBase64)
CxrApi.getInstance().sendCustomViewIcons(listOf(icon1Info, vectorInfo))
完整流程（Drawable 转 Bitmap 再转 Base64）见 CXRMSamples CustomViewViewModel.uploadIcon。

其中，Lottie 动画上传通过 CxrApi.getInstance().sendCustomView_LottieAnimJson(lottieViewId, lottieJson) 完成，通常在从 raw 资源中读取动画 Json 字符串之后调用。必须在 openCustomView 之前完成上传，确保打开自定义页面可显示动画。

打开自定义页面
调用 CxrApi.getInstance().openCustomView(selfViewJson: String)，参数为描述整棵视图树的 JSON 字符串。JSON 结构为树形：根节点与子节点均为带 type、props、children（可选）的对象；type 可为 LinearLayout、RelativeLayout、TextView、ImageView 等；props 为对应类型的属性（如 layout_width、layout_height、text、textColor、backgroundColor、name、scaleType 等）。具体字段与取值见示例中的 SelfViewJson、LinearLayoutProps、TextViewProps、ImageViewProps、RelativeLayoutProps 等。

// selfView 为 SelfViewJson 树，通过 toJson() 得到与「使用案例」中初始化 json 一致的字符串
CxrApi.getInstance().openCustomView(selfView.toJson())
更新页面
在页面已打开的前提下，调用 CxrApi.getInstance().updateCustomView(updateViewJson: String) 可只更新指定子节点，无需传整棵树。updateViewJson 为 UpdateViewJson 序列化后的 JSON，其中包含要更新的节点 id 及需要变更的 props（如 text、name 等）。参考 UpdateViewJson.kt 与 CustomViewViewModel 中的 updateCustomView 逻辑。

val updateViewJson = UpdateViewJson().apply {
    updateList.add(UpdateViewJson.UpdateJson(id = "textView").apply { props["text"] = "Hello Rokid" })
    updateList.add(UpdateViewJson.UpdateJson(id = "imageView").apply { props["name"] = "icon1" })
}
CxrApi.getInstance().updateCustomView(updateViewJson.toJson())
控制 Lottie 动画
Lottie 动画可通过 CxrApi.getInstance().sendCustomView_LottieAnimControl("lottieView", controlType) 单独控制动画播放状态，该接口只负责控制，不负责上传资源。

示例流程参考 CXRMSamples 中的 CustomViewViewModel.controlLottieAnim：

调用 sendCustomView_LottieAnimControl("lottieView", LottieAnimControl.PLAY) 开始播放动画。
调用 sendCustomView_LottieAnimControl("lottieView", LottieAnimControl.PAUSE) 暂停播放。
调用 sendCustomView_LottieAnimControl("lottieView", LottieAnimControl.RESUME) 继续播放。
调用 sendCustomView_LottieAnimControl("lottieView", LottieAnimControl.CANCEL) 取消当前动画。
调用 sendCustomView_LottieAnimControl("lottieView", LottieAnimControl.REVERSESPEED) 反转播放速度等。
关闭自定义页面
调用 CxrApi.getInstance().closeCustomView() 关闭眼镜端当前显示的自定义页面。

CxrApi.getInstance().closeCustomView()
使用案例
初始化 json
{
  "type": "LinearLayout",
  "props": {
    "layout_width": "match_parent",
    "layout_height": "match_parent",
    "orientation": "vertical",
    "gravity": "center_horizontal",
    "paddingTop": "140dp",
    "paddingBottom": "100dp",
    "backgroundColor": "#FF000000"
  },
  "children": [
    {
      "type": "TextView",
      "props": {
        "id": "tv_title",
        "layout_width": "wrap_content",
        "layout_height": "wrap_content",
        "text": "Init Text",
        "textSize": "16sp",
        "textColor": "#FF00FF00",
        "textStyle": "bold",
        "marginBottom": "20dp"
      }
    },
    {
      "type": "RelativeLayout",
      "props": {
        "width": "match_parent",
        "height": "100dp",
        "backgroundColor": "#00000000",
        "padding": "10dp"
      },
      "children": [
        {
          "type": "ImageView",
          "props": {
            "id": "iv_icon",
            "layout_width": "60dp",
            "layout_height": "60dp",
            "name": "icon_name0",
            "layout_alignParentStart": "true",
            "layout_centerVertical": "true"
          }
        },
        {
          "type": "TextView",
          "props": {
            "id": "tv_text",
            "layout_width": "wrap_content",
            "layout_height": "wrap_content",
            "text": "Text to the end of Icon",
            "textSize": "16sp",
            "textColor": "#FF00FF00",
            "layout_toEndOf": "iv_icon",
            "layout_centerVertical": "true",
            "marginStart": "15dp"
          }
        }
      ]
    }
  ]
}
LottieAnimationView 节点示例：

{
  "type": "com.airbnb.lottie.LottieAnimationView",
  "props": {
    "id": "lottie_view0",
    "layout_width": "580dp",
    "layout_height": "161dp",
    "marginTop": "26dp",
    "app:lottie_autoPlay": "true",
    "app:lottie_repeatCount": 5,
    "layout_centerVertical": "true"
  }
}
更新 json
[
  {
    "action": "update",
    "id": "tv_title",
    "props": {
      "text": "Update Text"
    }
  },
  {
    "action": "update",
    "id": "iv_icon",
    "props": {
      "name": "icon_name1"
    }
  }
]
上述示例与 SelfViewJson / UpdateViewJson 数据结构一一对应，可直接参考示例工程中的实现。

移动端自定义指令
在进行自定义指令操作之前，请先完成设备连接。

Tips:参考示例代码中的：CustomProtocolActivity.kt、CustomProtocolViewModel.kt

概述
自定义指令用于移动端与基于 CXR-S SDK 开发的眼镜端应用进行自定义协议交互。移动端可发送带 key 与 Caps 负载的指令，并监听眼镜端回传的自定义指令。

设置监听
通过 CxrApi.getInstance().setCustomCmdListener(listener: CustomCmdListener?) 设置或移除自定义指令监听。CustomCmdListener 回调为 (key: String?, caps: Caps?) -> Unit：当眼镜端发送自定义指令时，会带上 key 与 Caps 数据；应用可根据 key 区分业务，并从 Caps 中解析出字符串、整型、浮点、二进制、嵌套 Caps 等。传入 null 可移除监听。

private val customCmdListener = CustomCmdListener { key, caps ->
    if (key == CONSTANT.CUSTOM_CMD) {
        caps?.let { parseCMD(it) }
    }
}
CxrApi.getInstance().setCustomCmdListener(customCmdListener)
// 移除监听
CxrApi.getInstance().setCustomCmdListener(null)
发送自定义指令
调用 CxrApi.getInstance().sendCustomCmd(key: String, caps: Caps) 向眼镜端发送一条自定义指令。其中 key 为自定义键名（如示例中的 CONSTANT.CUSTOM_CMD），用于与眼镜端约定协议；caps 为 Caps 对象，可写入多种类型（如 write、writeInt32、嵌套 Caps 等）。眼镜端 CXR-S 应用需实现对应 key 的接收与处理逻辑。

val caps = Caps().apply {
    write("Custom String Message:")
    writeInt32(123)
    write(true)
    write(Caps().apply { write("Nested String Message") })
}
CxrApi.getInstance().sendCustomCmd("Custom Message", caps)
Caps 数据解析
收到 CustomCmdListener 回调时，可通过 Caps 的 size()、at(index) 及 Value 的 type()、string、int、float、binary、object 等按写入顺序解析。类型包括 TYPE_STRING、TYPE_INT32、TYPE_FLOAT、TYPE_BINARY、TYPE_DOUBLE、TYPE_UINT32、TYPE_OBJECT 等。参考 CustomProtocolViewModel 中的 parseCMD 与 sendCustomMessage。

private fun parseCMD(caps: Caps): String {
    val size = caps.size()
    val sb = StringBuilder()
    for (i in 0 until size) {
        val value = caps.at(i)
        when (value.type()) {
            Caps.Value.TYPE_STRING -> sb.append(value.string)
            Caps.Value.TYPE_INT32 -> sb.append(value.int)
            Caps.Value.TYPE_FLOAT -> sb.append(value.float)
            Caps.Value.TYPE_BINARY -> sb.append(Base64.encodeToString(value.binary.data, Base64.DEFAULT))
            Caps.Value.TYPE_DOUBLE -> sb.append(value.double)
            Caps.Value.TYPE_UINT32 -> sb.append(value.int)
            Caps.Value.TYPE_OBJECT -> sb.append(parseCMD(value.`object`))
            else -> sb.append("?")
        }
    }
    return sb.toString()
}
参考示例
CustomProtocolActivity.kt、CustomProtocolViewModel.kt：设置监听、发送指令与解析 Caps。


