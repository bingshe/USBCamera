# USBCamera
机顶盒USB相机使用

注释事项：
1、代码只是一个module，需要导入androidstudio
2、运行时需要将libImageProc.so放到/system/lib目录
3、找到USB摄像头的设备节点（一般的dev目录下的video0或者video1），赋777权限
4、录下的视频在sdcard目录
5、so库对应的jni，主要是对摄像头的设备节点进行操作


USB摄像头使用问题记录：
1、如果应用挂掉，没有释放摄像头资源，会导致摄像头无法使用，需拔插或者等待3分钟以上才能正常使用。
