
/*
 * Copyright  2019 Blockchain Technology and Application Joint Lab, Linkel Technology Co., Ltd, Beijing, Fintech Research Center of ISCAS.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BA SIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package rep.sc.tpl

import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization.{read, writePretty}
import rep.proto.rc2.{ActionResult, ChaincodeId}
import rep.sc.scalax.IContract
import rep.sc.scalax.ContractContext
import rep.sc.scalax.ContractException
import rep.sc.tpl.did.DidTplPrefix.signerPrefix
import rep.sc.tpl.ProofDataSingle
import org.json4s.native.Serialization.write
import org.json4s.{DefaultFormats, jackson}
import rep.utils.SerializeUtils

import scala.collection.immutable.HashMap
import scala.collection.mutable

/**
 * 传感器数据存证合约
 */

class Sensors2Chain extends IContract {
    implicit val formats = DefaultFormats
    type JustMap = Map[String, Any]
    val DEVICE_PREFIX = "device/"
    val RECORD_PREFIX = "record/"
    val PROJECT_PREFIX = "project/"

    def init(ctx: ContractContext) {
        println(s"tid: ${ctx.t.id}")
    }

    def keysExist(data: JustMap, key_set: Set[String]): Boolean = {
        key_set.subsetOf(data.keySet)
    }

    def map2Json(justMap: JustMap): String = {
        return writePretty(justMap)
    }

    def json2Map(json: String): JustMap = {
        return read[JustMap](json)
    }

    def saveKV(ctx: ContractContext, prefix: String, key: String, data: JustMap) = {
        val k = prefix + key
        ctx.api.setVal(k, map2Json(data))
    }

    def loadKV(ctx: ContractContext, prefix: String, key: String): Option[JustMap] = {
        val k = prefix + key
        val v = ctx.api.getVal(k)
        if (v == null) {
            return None
        } else {
            return Some(json2Map(v.asInstanceOf[String]))
        }
    }

    def isRegistered(ctx: ContractContext, id: String): Boolean = {
        val v = loadKV(ctx, DEVICE_PREFIX, id)
        val v2 = loadKV(ctx,PROJECT_PREFIX, id)
        if (v.isDefined || v2.isDefined) {
            return true
        } else {
            return false
        }
    }


    def save_record(ctx: ContractContext, data: JustMap): ActionResult = {
        if (!keysExist(data, Set("deviceID", "records"))) {
            throw ContractException("save_record必填属性不全，请检查")
        }
        val deviceID = data("deviceID").asInstanceOf[String]
        // 检查是不是注册过
        if (!isRegistered(ctx, deviceID)) {
            throw new ContractException("deviceID[" + deviceID + "]尚未注册")
        }
        val records = data("records").asInstanceOf[List[JustMap]]
        if (records.isEmpty) {
            throw new ContractException("提交的数据记录为空")
        }
        for (item <- records) {
            val ts = item.getOrElse("ts", null)
            if (ts == null) {
                throw new ContractException("数据记录缺少ts属性")
            }
            var recordv = item - "ts"
            recordv = recordv.updated("deviceID", deviceID)
            saveKV(ctx, RECORD_PREFIX, ts.asInstanceOf[BigInt].toString, recordv)
        }
        ActionResult()
    }

    def register_device(ctx: ContractContext, data: JustMap): ActionResult = {
        if (!keysExist(data, Set("deviceID", "description", "projectID"))) {
            throw ContractException("register_device必填属性不全，请检查")
        }
        val deviceID = data("deviceID").asInstanceOf[String]
        if (isRegistered(ctx, deviceID)) {
            throw new ContractException("deviceID[" + deviceID + "]已存在");
        }
        val description = data("description").asInstanceOf[String]
        val projectID = data("projectID").asInstanceOf[String]
        if (isRegistered(ctx, projectID)) {
            throw new ContractException("projectID[" + projectID + "]尚未注册存在");
        }

        val creditCode = ctx.t.getSignature.getCertId.creditCode
        val certName = ctx.t.getSignature.getCertId.certName

        val rv: JustMap = Map("deviceID" -> deviceID, "projectID"->projectID , "description" -> description, "creditCode" -> creditCode, "certName" -> certName)
        saveKV(ctx, DEVICE_PREFIX, deviceID, rv)
        ActionResult()
    }
    def register_project(ctx: ContractContext, data: JustMap): ActionResult={
        if (!keysExist(data, Set("projectID", "description"))) {
            throw ContractException("register_project必填属性不全，请检查")
        }
        val projectID = data("projectID").asInstanceOf[String]
        val description = data("description").asInstanceOf[String]
        val creditCode = ctx.t.getSignature.getCertId.creditCode
        val certName = ctx.t.getSignature.getCertId.certName
        if (isRegistered(ctx, projectID)) {
            throw new ContractException("projectID[" + projectID + "]已存在");
        }
        val rv: JustMap = Map("projectID" -> projectID, "description" -> description, "creditCode" -> creditCode, "certName" -> certName)
        saveKV(ctx, PROJECT_PREFIX, projectID, rv)
        ActionResult()
    }

    /**
     * 根据action,找到对应的method，并将传入的json字符串parse为method需要的传入参数
     */
    def onAction(ctx: ContractContext, action: String, sdata: String): ActionResult = {
        val json = parse(sdata)
        action match {
            case "register_device" =>
                register_device(ctx, json.extract[Map[String, Any]])
            case "save_record" =>
                save_record(ctx, json.extract[Map[String, Any]])
            case "register_project"=>
                register_project(ctx, json.extract[Map[String, Any]])
        }
    }

}
