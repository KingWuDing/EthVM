package io.enkrypt.kafka.connect.sinks.mongo

import arrow.core.Option
import io.enkrypt.common.extensions.hex
import io.enkrypt.common.extensions.unsignedBigInteger
import org.apache.kafka.connect.data.Schema
import org.apache.kafka.connect.data.Schema.Type.ARRAY
import org.apache.kafka.connect.data.Schema.Type.BOOLEAN
import org.apache.kafka.connect.data.Schema.Type.BYTES
import org.apache.kafka.connect.data.Schema.Type.FLOAT32
import org.apache.kafka.connect.data.Schema.Type.FLOAT64
import org.apache.kafka.connect.data.Schema.Type.INT16
import org.apache.kafka.connect.data.Schema.Type.INT32
import org.apache.kafka.connect.data.Schema.Type.INT64
import org.apache.kafka.connect.data.Schema.Type.INT8
import org.apache.kafka.connect.data.Schema.Type.MAP
import org.apache.kafka.connect.data.Schema.Type.STRING
import org.apache.kafka.connect.data.Schema.Type.STRUCT
import org.apache.kafka.connect.data.Struct
import org.bson.BsonArray
import org.bson.BsonBinary
import org.bson.BsonBoolean
import org.bson.BsonDecimal128
import org.bson.BsonDocument
import org.bson.BsonDouble
import org.bson.BsonInt32
import org.bson.BsonInt64
import org.bson.BsonNull
import org.bson.BsonString
import org.bson.BsonValue
import org.bson.types.Decimal128
import java.math.BigDecimal
import java.math.MathContext
import java.nio.ByteBuffer

object StructToBsonConverter {

  private val HEX_FIELDS = setOf(
    "hash",
    "parentHash",
    "unclesHash",
    "coinbase",
    "stateRoot",
    "txTrieRoot",
    "receiptTrieRoot",
    "logsBloom",
    "mixHash",
    "nonce",
    "extraData",
    "from",
    "to",
    "data",
    "postTxState",
    "bloomFilter",
    "contract",
    "tokenId",
    "address",
    "txHash",
    "creator",
    "blockHash",
    "miner",
    "sha3Uncles",
    "transactionsRoot",
    "receiptsRoot",
    "input",
    "r",
    "s",
    "author"
  )

  private val UNSIGNED_BIG_INTEGERS_FIELDS = setOf(
    "difficulty",
    "totalDifficulty",
    "cumulativeGas",
    "bigIntegerValue",
    "gasPrice",
    "gasLimit",
    "gasUsed",
    "gasLeftover",
    "gasRefund",
    "reward",
    "value",
    "amount",
    "blockNumber",
    "transactionIndex",
    "gas",
    "balance",
    "blockNumber"
  )

  private val BASIC_CONVERTERS = mapOf(
    BOOLEAN to { v: Any -> BsonBoolean(v as Boolean) },
    INT8 to { v: Any -> BsonInt32((v as Byte).toInt()) },
    INT16 to { v: Any -> BsonInt32((v as Short).toInt()) },
    INT32 to { v: Any -> BsonInt32(v as Int) },
    INT64 to { v: Any -> BsonInt64(v as Long) },
    FLOAT32 to { v: Any -> BsonDouble((v as Float).toDouble()) },
    FLOAT64 to { v: Any -> BsonDouble(v as Double) },
    STRING to { v: Any -> BsonString(v as String) },
    BYTES to { v: Any -> BsonBinary((v as ByteBuffer).array()) }
  )

  fun convert(value: Any?, allowNulls: Boolean = true): BsonDocument =
    Option
      .fromNullable(value)
      .fold(
        { BsonDocument() },
        {

          val struct = (it as Struct)
          val schema = struct.schema()

          val doc = BsonDocument()

          schema.fields().forEach { field ->

            // TODO make field conversion more generic and respect object structure

            var bsonValue = convertField(field.schema(), struct.get(field), allowNulls)

            if (bsonValue != null) {

              val fieldName = field.name()

              if (bsonValue.isBinary) {

                val bytes = (bsonValue as BsonBinary).data

                if (HEX_FIELDS.contains(fieldName)) {
                  bsonValue = BsonString(bytes.hex())
                } else if (UNSIGNED_BIG_INTEGERS_FIELDS.contains(fieldName)) {

                  val bigDecimal =
                    if (bytes.isEmpty())
                      BigDecimal.ZERO
                    else
                      bytes.unsignedBigInteger().toBigDecimal(0, MathContext.DECIMAL128)

                  bsonValue = BsonDecimal128(Decimal128(bigDecimal))
                }
              }

              doc.append(fieldName, bsonValue)
            }
          }

          doc
        })

  private fun convertField(schema: Schema, value: Any?, allowNulls: Boolean): BsonValue? {
    val type = schema.type()
    return when (type) {
      in Schema.Type.values().filterNot { it == STRUCT || it == ARRAY || it == MAP } -> convertField(value, allowNulls, BASIC_CONVERTERS[type]!!)
      ARRAY -> convertArray(schema, value)
      STRUCT -> convert(value, allowNulls)
      else -> throw IllegalArgumentException("Unhandled Schema type: $type")
    }
  }

  private fun convertField(value: Any?, allowNulls: Boolean, bsonFactory: (Any) -> BsonValue?): BsonValue? =
    Option
      .fromNullable(value)
      .fold(
        { if (allowNulls) BsonNull() else null },
        bsonFactory
      )

  private fun convertArray(schema: Schema, value: Any?): BsonValue {

    val valueSchema = schema.valueSchema()

    return Option
      .fromNullable(value)
      .fold(
        { BsonArray() },
        { data ->

          val bsonValues = (data as List<Any?>)
            .map { d ->
              Option.fromNullable(d)
                .fold(
                  { BsonNull() },
                  { convertField(valueSchema, d, true) }
                )
            }

          BsonArray(bsonValues)
        }
      )
  }
}
