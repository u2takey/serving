package com.tencent.angel.serving.service.jersey.util

import com.tencent.angel.serving.apis.common.TypesProtos
import com.tencent.angel.serving.apis.prediction.RequestProtos.Request
import com.tencent.angel.serving.servables.common.SavedModelBundle
import com.tencent.angel.serving.service.ModelServer
import com.tencent.angel.utils.ProtoUtils
import org.slf4j.{Logger, LoggerFactory}
import org.json4s._
import org.json4s.native.JsonMethods._

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._

object Json2Instances {

  private val LOG: Logger = LoggerFactory.getLogger(getClass)
  private val kPredictRequestInstancesKey = "instances"
  private val sparseKey = "sparseIndices"
  private val sparseValue = "sparseValues"
  private val kBase64Key = "b64"

  def fillClassificationRequestFromJson(): Unit ={

  }

  def fillPredictRequestFromJson(requestBody: String, requestBuilder: Request.Builder): Unit ={
    try {
      val jsonObject = parse(requestBody)
      val instances = jsonObject \ Json2Instances.kPredictRequestInstancesKey
      if(!instances.isInstanceOf[JNothing.type]) {
        if(!instances.isInstanceOf[JArray] || instances.asInstanceOf[JArray].values.isEmpty) {
          throw new Exception("Request data format error.")
        }
        fillInstancesFromInstancesList(instances.asInstanceOf[JArray], requestBuilder)
      } else {
        throw new Exception("Missing 'instances' key")
      }
    } catch {
      case ex: Exception => ex.printStackTrace()
    }
  }


  def fillInstancesFromInstancesList(instances: JArray, requestBuilder: Request.Builder): Unit = {
    //fill instance from json data, read docs/restful-api.md to know details data format.
    val servableRequest = ModelServer.getServerCore.servableRequestFromModelSpec(requestBuilder.getModelSpec)
    val servableHandle = ModelServer.getServerCore.servableHandle[SavedModelBundle](servableRequest)
    val (keyType, valueType, dim) = servableHandle.servable.getInputInfo()
    val isElementObject = (value: Object) => {
      value.isInstanceOf[JObject] && !isValBase64Object(value)
    }
    val elementsAreObjects = isElementObject(instances(0))
    var instanceCount = 0
    for(ele <- instances.arr) {
      if(elementsAreObjects) {
        if(!isElementObject(ele)) {
          throw new Exception("Expecting object but got list at item " + instanceCount + " of input list.")
        }
        if(dim < 0) {
          //pmml prediction data parse
          if(ele.asInstanceOf[JObject].values.size <= 2) {
            var instanceName: String = ""
            var example: Map[String, Any] = null
            for((_, values) <- ele.asInstanceOf[JObject].obj) {
              values match {
                case jObject: JObject =>
                  valueType match {
                    case TypesProtos.DataType.DT_STRING =>
                      example = jObject.values.map { case (k, v) => (k, v.asInstanceOf[String]) }
                    case TypesProtos.DataType.DT_INT32 =>
                      example = jObject.values.map { case (k, v) => (k, v.asInstanceOf[BigInt].toInt) }
                    case TypesProtos.DataType.DT_INT64 =>
                      example = jObject.values.map { case (k, v) => (k, v.asInstanceOf[BigInt].toLong) }
                    case TypesProtos.DataType.DT_FLOAT =>
                      example = jObject.values.map { case (k, v) => (k, v.asInstanceOf[Double].toFloat) }
                    case TypesProtos.DataType.DT_DOUBLE =>
                      example = jObject.values.map { case (k, v) => (k, v.asInstanceOf[Double]) }
                    case _ => new Exception("unsuported data type!")
                  }
                case _ =>
                  instanceName = values.toString
              }
            }
            val instance = ProtoUtils.getInstance(instanceName, example.asJava)
            requestBuilder.addInstances(instance)
          } else{
            var example: Map[String, Any] = null
            valueType match {
              case TypesProtos.DataType.DT_STRING =>
                example = ele.asInstanceOf[JObject].values.map{case (k, v) => (k, v.asInstanceOf[String])}
              case TypesProtos.DataType.DT_INT32 =>
                example = ele.asInstanceOf[JObject].values.map{case (k, v) => (k, v.asInstanceOf[BigInt].toInt)}
              case TypesProtos.DataType.DT_INT64 =>
                example = ele.asInstanceOf[JObject].values.map{case (k, v) => (k, v.asInstanceOf[BigInt].toLong)}
              case TypesProtos.DataType.DT_FLOAT =>
                example = ele.asInstanceOf[JObject].values.map{case (k, v) => (k, v.asInstanceOf[Double].toFloat)}
              case TypesProtos.DataType.DT_DOUBLE =>
                example = ele.asInstanceOf[JObject].values.map{case (k, v) => (k, v.asInstanceOf[Double])}
              case _ => new Exception("unsuported data type!")
            }
            val instance = ProtoUtils.getInstance(example.asJava)
            requestBuilder.addInstances(instance)
          }
        } else {
          //angel prediction data parse
          ele.asInstanceOf[JObject] \ Json2Instances.sparseKey match {
            case JNothing =>
              if(ele.asInstanceOf[JObject].values.size <= 2) {
                var instanceName: String = ""
                for((key, values) <- ele.asInstanceOf[JObject].obj) {
                  values match {
                    case array: JArray =>
                      var example: List[Any] = null
                      valueType match {
                        case TypesProtos.DataType.DT_STRING =>
                          example = array.values.map(x => x.asInstanceOf[String])
                        case TypesProtos.DataType.DT_INT32 =>
                          example = array.values.map(x => x.asInstanceOf[BigInt].toInt)
                        case TypesProtos.DataType.DT_INT64 =>
                          example = array.values.map(x => x.asInstanceOf[BigInt].toLong)
                        case TypesProtos.DataType.DT_FLOAT =>
                          example = array.values.map(x => x.asInstanceOf[Double].toFloat)
                        case TypesProtos.DataType.DT_DOUBLE =>
                          example = array.values.map(x => x.asInstanceOf[Double])
                        case _ => new Exception("unsuported data type!")
                      }
                      val instance = ProtoUtils.getInstance(instanceName, example.iterator.asJava)
                      requestBuilder.addInstances(instance)
                    case jObject: JObject =>
                      var example: Map[String, Any] = null
                      valueType match {
                        case TypesProtos.DataType.DT_STRING =>
                          example = jObject.values.map{case (k, v) => (k, v.asInstanceOf[String])}
                        case TypesProtos.DataType.DT_INT32 =>
                          example = jObject.values.map{case (k, v) => (k, v.asInstanceOf[BigInt].toInt)}
                        case TypesProtos.DataType.DT_INT64 =>
                          example = jObject.values.map{case (k, v) => (k, v.asInstanceOf[BigInt].toLong)}
                        case TypesProtos.DataType.DT_FLOAT =>
                          example = jObject.values.map{case (k, v) => (k, v.asInstanceOf[Double].toFloat)}
                        case TypesProtos.DataType.DT_DOUBLE =>
                          example = jObject.values.map{case (k, v) => (k, v.asInstanceOf[Double])}
                        case _ => new Exception("unsuported data type!")
                      }
                      val instance = ProtoUtils.getInstance(instanceName, example.asJava)
                      requestBuilder.addInstances(instance)
                    case _ =>
                      instanceName = values.toString
                  }
                }

              } else {
                var example: Map[String, Any] = null
                //val example = ele.asInstanceOf[JObject].values
                valueType match {
                  case TypesProtos.DataType.DT_STRING =>
                    example = ele.asInstanceOf[JObject].values.map{case (k, v) => (k, v.asInstanceOf[String])}
                  case TypesProtos.DataType.DT_INT32 =>
                    example = ele.asInstanceOf[JObject].values.map{case (k, v) => (k, v.asInstanceOf[BigInt].toInt)}
                  case TypesProtos.DataType.DT_INT64 =>
                    example = ele.asInstanceOf[JObject].values.map{case (k, v) => (k, v.asInstanceOf[BigInt].toLong)}
                  case TypesProtos.DataType.DT_FLOAT =>
                    example = ele.asInstanceOf[JObject].values.map{case (k, v) => (k, v.asInstanceOf[Double].toFloat)}
                  case TypesProtos.DataType.DT_DOUBLE =>
                    example = ele.asInstanceOf[JObject].values.map{case (k, v) => (k, v.asInstanceOf[Double])}
                  case _ => new Exception("unsuported data type!")
                }
                val instance = ProtoUtils.getInstance(example.asJava)
                requestBuilder.addInstances(instance)
              }
            case arrIndices: JArray =>
              ele.asInstanceOf[JObject] \ Json2Instances.sparseValue match {
                case arrValues: JArray =>
                  keyType match {
                    case TypesProtos.DataType.DT_INT32 =>
                      val indices = arrIndices.values.map(x => x.asInstanceOf[BigInt].toInt.asInstanceOf[java.lang.Integer])
                      var example: List[Any] = null
                      valueType match {
                        case TypesProtos.DataType.DT_STRING =>
                          example = arrValues.values.map(x => x.asInstanceOf[String])
                        case TypesProtos.DataType.DT_INT32 =>
                          example = arrValues.values.map(x => x.asInstanceOf[BigInt].toInt)
                        case TypesProtos.DataType.DT_INT64 =>
                          example = arrValues.values.map(x => x.asInstanceOf[BigInt].toLong)
                        case TypesProtos.DataType.DT_FLOAT =>
                          example = arrValues.values.map(x => x.asInstanceOf[Double].toFloat)
                        case TypesProtos.DataType.DT_DOUBLE =>
                          example = arrValues.values.map(x => x.asInstanceOf[Double])
                        case _ => new Exception("unsuported data type!")
                      }
                      val instance = ProtoUtils.getInstance(dim.asInstanceOf[Int], indices.zip(example).toMap.asJava)
                      requestBuilder.addInstances(instance)
                    case TypesProtos.DataType.DT_INT64 =>
                      val indices = arrIndices.values.map(x => x.asInstanceOf[BigInt].toLong.asInstanceOf[java.lang.Long])
                      var example: List[Any] = null
                      valueType match {
                        case TypesProtos.DataType.DT_STRING =>
                          example = arrValues.values.map(x => x.asInstanceOf[String])
                        case TypesProtos.DataType.DT_INT32 =>
                          example = arrValues.values.map(x => x.asInstanceOf[BigInt].toInt)
                        case TypesProtos.DataType.DT_INT64 =>
                          example = arrValues.values.map(x => x.asInstanceOf[BigInt].toLong)
                        case TypesProtos.DataType.DT_FLOAT =>
                          example = arrValues.values.map(x => x.asInstanceOf[Double].toFloat)
                        case TypesProtos.DataType.DT_DOUBLE =>
                          example = arrValues.values.map(x => x.asInstanceOf[Double])
                        case _ => new Exception("unsuported data type!")
                      }
                      val instance = ProtoUtils.getInstance(dim.asInstanceOf[Long], indices.zip(example).toMap.asJava)
                      requestBuilder.addInstances(instance)
                    case _ => new Exception("unsuported key data type!")
                  }
                case _ => throw new Exception("Json format error!")
              }
            case _ => throw new Exception("Json format error!")
          }
        }
      } else {
        //angel prediction data parse
        if(isElementObject(ele)) {
          throw new Exception("Expecting value/list but got object at item " + instanceCount + " of input list")
        }
        ele match {
          case array: JArray =>
            var example: List[Any] = null
            valueType match {
              case TypesProtos.DataType.DT_STRING =>
                example = array.values.map(x => x.asInstanceOf[String])
              case TypesProtos.DataType.DT_INT32 =>
                example = array.values.map(x => x.asInstanceOf[BigInt].toInt)
              case TypesProtos.DataType.DT_INT64 =>
                example = array.values.map(x => x.asInstanceOf[BigInt].toLong)
              case TypesProtos.DataType.DT_FLOAT =>
                example = array.values.map(x => x.asInstanceOf[Double].toFloat)
              case TypesProtos.DataType.DT_DOUBLE =>
                example = array.values.map(x => x.asInstanceOf[Double])
              case _ => new Exception("unsuported data type!")
            }
            val instance = ProtoUtils.getInstance(example.iterator.asJava)
            requestBuilder.addInstances(instance)
          case _ => new Exception("Json format error!")
        }
      }
      instanceCount = instanceCount + 1
    }
  }

  def isValBase64Object(value: Object): Boolean ={
    value match {
      case nObject: JObject =>
        val base64String = nObject.values.get(kBase64Key)
        if (base64String.nonEmpty && nObject.values.size() == 1) {
          return true
        }
      case _ =>
    }
    false
  }

}


