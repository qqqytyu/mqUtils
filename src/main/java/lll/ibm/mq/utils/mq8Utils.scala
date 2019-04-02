package lll.ibm.mq.utils

import com.ibm.mq.constants.MQConstants
import com.ibm.mq._
import scala.collection.mutable

class mq8Utils {

    var qMgr:MQQueueManager = _

    var qmName:String = _

    var qName:String = _

    var waitInt:Int = _

    def this(conf:mutable.HashMap[String,String]){
        this()
        if(conf.size != 9) throw new Exception("input parameter mismatch！")
        conf.foreach {
            case ("hostname", value) => MQEnvironment.hostname = value
            case ("channel", value) => MQEnvironment.channel = value
            case ("port", value) => MQEnvironment.port = value.toInt
            case ("CCSID", value) => MQEnvironment.CCSID = value.toInt
            case ("userID", value) => MQEnvironment.userID = value
            case ("password", value) => MQEnvironment.password = value
            case ("qmName", value) => qmName = value
            case ("qName", value) => qName = value
            case ("waitInt", value) => waitInt = value.toInt
            case _ => throw new Exception("input parameter exceed expectation！")
        }
        qMgr = new MQQueueManager(qmName)
    }

    def receiveMsg(num:Int): Option[String] ={

        if(qMgr == null) throw new Exception("MQQueueManager is not initialize")

        val data:mutable.StringBuilder = new mutable.StringBuilder()

        try {

            //设置将要连接的队列属性
            val openOptions: Int =
                MQConstants.getIntValue("MQOO_INPUT_AS_Q_DEF") |
                        MQConstants.getIntValue("MQOO_OUTPUT") |
                        MQConstants.getIntValue("MQOO_INQUIRE")

            val gmo: MQGetMessageOptions = new MQGetMessageOptions()

            //在同步点控制下获取消息
            gmo.options = gmo.options + MQConstants.getIntValue("MQGMO_SYNCPOINT")

            //如果在队列上没有消息则等待
            gmo.options = gmo.options + MQConstants.getIntValue("MQGMO_FAIL_IF_QUIESCING")

            //如果队列管理器停顿则失败
            gmo.waitInterval = waitInt  //设置等待的毫秒时间限制

            /*关闭了就重新打开*/
            if (qMgr == null || !qMgr.isConnected()){
                qMgr = new MQQueueManager(qmName)
            }

            val queue: MQQueue = qMgr.accessQueue(qName, openOptions)

            //当前队列深度
            var depth:Int = queue.getCurrentDepth

            //一次最多取num条
            if(num != -1 && depth >= num) depth = num

            for(_ <- 1 to depth){
                // 要读的队列的消息
                val retrieve: MQMessage = new MQMessage()

                // 从队列中取出消息
                queue.get(retrieve, gmo)

                val bytes: Array[Byte] = new Array[Byte](retrieve.getMessageLength)

                retrieve.readFully(bytes)

                data.append(new String(bytes, "UTF-8").concat("\n"))
            }

            queue.close()

            Some(data.toString)

        } catch {
            case ex:MQException =>
                if(ex.completionCode != 2 && ex.reasonCode != 2033){
                    println("A WebSphere MQ error occurred : Completion code "
                            + ex.completionCode + " Reason code " + ex.reasonCode)
                    None
                } else {
                    closeMq()
                    throw ex
                }

            case ex:Exception =>
                closeMq()
                throw ex
        }
    }

    def sendMessage(mess:String): Unit ={

        try {

            //设置将要连接的队列属性
            val openOptions:Int =
                MQConstants.getIntValue("MQOO_OUTPUT") | MQConstants.getIntValue("MQOO_FAIL_IF_QUIESCING")

            /*关闭了就重新打开*/
            if (qMgr == null || !qMgr.isConnected()) {
                qMgr = new MQQueueManager(qmName)
            }

            val queue:MQQueue = qMgr.accessQueue(qName, openOptions)

            //定义一个简单的消息
            val putMessage:MQMessage  = new MQMessage()

            //将数据放入消息缓冲区
            putMessage.writeUTF(mess)

            //设置写入消息的属性（默认属性）
            val pmo:MQPutMessageOptions = new MQPutMessageOptions()

            //将消息写入队列
            queue.put(putMessage, pmo)

            queue.close()

        } catch {
            case ex:MQException =>
                println("A WebSphere MQ error occurred : Completion code "
                        + ex.completionCode + " Reason code " + ex.reasonCode)
            case ex:Exception =>
                closeMq()
                throw ex
        }
    }

    def closeMq(): Unit ={

        qMgr.disconnect()

    }

}

object mq8Utils{

    def apply(conf:mutable.HashMap[String,String]): mq8Utils = new mq8Utils(conf)

    def main(args: Array[String]): Unit = {
        val map:mutable.HashMap[String,String] = new mutable.HashMap[String,String]()
        map.put("hostname","10.110.80.173")
        map.put("channel","TEST.PSDP")
        map.put("port","1415")
        map.put("CCSID","1381")
        map.put("userID","mqm")
        map.put("password","mqm2017")
        map.put("qmName","CEAKNH")
        map.put("qName","T.SSDATA.PSDP.KN")
        map.put("waitInt","5000")
        val mq = mq8Utils(map)

        var isRun = 0
        while (isRun <=10){
            try {
                mq.receiveMsg(1000) match{
                    case Some(value) =>
                        isRun += 1
                        println(value)
                    case None =>
                }
            } catch {
                case ex:Exception => ex.printStackTrace()
            } finally {
                mq.closeMq()
            }
        }
    }

}
