package edu.upenn.turbo

import org.scalatra._
/*import org.json4s.{DefaultFormats, Formats}
import org.scalatra.json._
import org.json4s._
import org.json4s.JsonDSL._*/
import org.json4s.MappingException
import java.nio.file.NoSuchFileException

import java.nio.file.{Files, Paths}
import java.nio.file.attribute.BasicFileAttributes

// RDF4J imports
import org.eclipse.rdf4j.rio._
import org.eclipse.rdf4j.repository.Repository
import org.eclipse.rdf4j.repository.RepositoryConnection
import org.eclipse.rdf4j.query.QueryLanguage
import org.eclipse.rdf4j.model.ValueFactory
import org.eclipse.rdf4j.repository.manager.RemoteRepositoryManager
import org.eclipse.rdf4j.query.TupleQuery
import org.eclipse.rdf4j.query.TupleQueryResult
import org.eclipse.rdf4j.OpenRDFException
import org.eclipse.rdf4j.repository.RepositoryConnection
import org.eclipse.rdf4j.query.BooleanQuery
import org.eclipse.rdf4j.query.BindingSet
import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.rio.RDFFormat

import org.json4s.{DefaultFormats, Formats}
import org.scalatra.json._
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.JsonMappingException

import org.slf4j.LoggerFactory

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import java.io.File

import org.neo4j.graphdb._
import org.neo4j.graphdb.factory.GraphDatabaseFactory
import org.apache.tinkerpop.gremlin.neo4j.structure.Neo4jGraph
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource

case class GraphUpdateTime(dateOfUpdate: String, timeOfUpdate: String)
case class MedFullNameInput(searchList: Array[String])
case class MedFullNameResults(resultsMap: HashMap[String, ArrayBuffer[String]])
case class MedicationFreeText(searchTerm: String)
case class DiagnosisFreeText(searchTerm: String)
case class DiagnosisTermInput(searchTerm: String)
case class OmopConceptIdInput(searchTerm: String)
case class OmopConceptIdUri(result: String)
case class OmopConceptMap(result: Map[String, String])
case class LuceneMedResults(searchTerm: String, searchResults: Array[Map[String, String]])
case class LuceneDiagResults(searchTerm: String, searchResults: Array[Map[String, String]])
case class DrugClassInputs(searchList: Array[String])
case class DiagnosisPathways(resultsList: Array[String])
case class DrugHopsResults(resultsList: Map[String, Array[String]])
case class DiagnosisCodeResult(searchTerm: String, resultsList: HashMap[String, ArrayBuffer[String]])
case class TwoDimensionalArrListResults(resultsList: Array[Array[String]])

class DashboardServlet extends ScalatraServlet with JacksonJsonSupport
{
  val graphDB: GraphDBConnector = new GraphDBConnector
  /*val neo4j: Neo4jConnector = new Neo4jConnector()
  val neo4jgraph = Neo4jGraphConnection.getGraph()*/
  val diagCxn = GraphDbConnection.getDiagConnection()
  val medCxn = GraphDbConnection.getMedConnection()
  val ontCxn = GraphDbConnection.getOntConnection()

  val logger = LoggerFactory.getLogger("turboAPIlogger")

  protected implicit val jsonFormats: Formats = DefaultFormats
  before()
  {
      contentType = formats("json")
  }

  post("/diagnoses/getDiseaseURIsFromICDCodes")
  {
      logger.info("Received a post request")
      var parsedResult: Array[String] = null
      try 
      { 
          val userInput = request.body
          logger.info("received: " + userInput)
          val extractedResult = parse(userInput).extract[DrugClassInputs]
          parsedResult = extractedResult.searchList
          logger.info("extracted search term")

          try
          {
              TwoDimensionalArrListResults(graphDB.getDiseaseURIs(parsedResult, diagCxn))
          }
          catch
          {
              case e: RuntimeException => InternalServerError(Map("message" -> "There was a problem retrieving results from the triplestore."))
          }
      } 
      catch 
      {
          case e1: JsonParseException => BadRequest(Map("message" -> "Unable to parse JSON"))
          case e2: MappingException => BadRequest(Map("message" -> "Unable to parse JSON"))
          case e3: JsonMappingException => BadRequest(Map("message" -> "Did not receive any content in the request body"))
      }
  }

  post("/diagnoses/getSemanticContextForDiseaseURIs")
  {
      logger.info("Received a post request")
      var parsedResult: Array[String] = null
      try 
      { 
          val userInput = request.body
          logger.info("received: " + userInput)
          val extractedResult = parse(userInput).extract[DrugClassInputs]
          parsedResult = extractedResult.searchList
          logger.info("extracted search term")

          try
          {
              TwoDimensionalArrListResults(graphDB.getSemanticContextForDiseaseURIs(parsedResult, diagCxn))
          }
          catch
          {
              case e: RuntimeException => InternalServerError(Map("message" -> "There was a problem retrieving results from the triplestore."))
          }
      } 
      catch 
      {
          case e1: JsonParseException => BadRequest(Map("message" -> "Unable to parse JSON"))
          case e2: MappingException => BadRequest(Map("message" -> "Unable to parse JSON"))
          case e3: JsonMappingException => BadRequest(Map("message" -> "Did not receive any content in the request body"))
      }
  }

  post("/diagnoses/getICDCodesFromDiseaseURI")
  {
      logger.info("Received a post request")
      var parsedResult: String = null
      try 
      { 
          val userInput = request.body
          logger.info("received: " + userInput)
          val extractedResult = parse(userInput).extract[DiagnosisTermInput]
          parsedResult = extractedResult.searchTerm
          logger.info("extracted search term")

          try
          { 
              DiagnosisCodeResult(parsedResult, graphDB.getDiagnosisCodes(parsedResult, diagCxn))
          }
          catch
          {
              case e: RuntimeException => InternalServerError(Map("message" -> "There was a problem retrieving results from the triplestore."))
          }
      } 
      catch 
      {
          case e1: JsonParseException => BadRequest(Map("message" -> "Unable to parse JSON"))
          case e2: MappingException => BadRequest(Map("message" -> "Unable to parse JSON"))
          case e3: JsonMappingException => BadRequest(Map("message" -> "Did not receive any content in the request body"))
      }
  }

  post("/medications/findOrderNamesFromInputURI")
  {
      logger.info("Received a post request")
      var parsedResult: Array[String] = null
      try 
      { 
          val userInput = request.body
          val extractedResult = parse(userInput).extract[MedFullNameInput]
          parsedResult = extractedResult.searchList
          logger.info("Input class: " + parsedResult)

          try
          {
              MedFullNameResults(graphDB.getMedicationFullNameResults(parsedResult, medCxn))
          }
          catch
          {
              case e: RuntimeException => 
              {
                logger.info("error:" + e)
                InternalServerError(Map("message" -> "There was a problem retrieving results from the triplestore."))
              }
          }
      } 
      catch 
      {
          case e1: JsonParseException => BadRequest(Map("message" -> "Unable to parse JSON"))
          case e2: MappingException => BadRequest(Map("message" -> "Unable to parse JSON"))
          case e3: JsonMappingException => BadRequest(Map("message" -> "Did not receive any content in the request body"))
      }
  }

  post("/medications/medicationTextSearch")
  {
      logger.info("Received a post request")
      var parsedResult: String = null
      try 
      { 
          val userInput = request.body
          val extractedResult = parse(userInput).extract[MedicationFreeText]
          parsedResult = extractedResult.searchTerm
          var topResults: Option[ArrayBuffer[ArrayBuffer[String]]] = None
          try
          {
              topResults = graphDB.getBestMatchTermForMedicationLookup(medCxn, parsedResult, 10)
          }
          catch
          {
              case e: RuntimeException => InternalServerError(Map("message" -> "There was a problem retrieving results from the triplestore."))
          }
          if (topResults == None)
          {
              val noContentMessage = "Your input of \"" + parsedResult + "\" returned no matches."
              NoContent(Map("message" -> noContentMessage))
          }
          else
          {
              var luceneResAsMaps = new ArrayBuffer[Map[String, String]]
              for (a <- topResults.get)
              {
                 var tempMap = Map("IRI" -> a(0), "label" -> a(1).replaceAll("@en", "").replaceAll("\"", ""))
                 luceneResAsMaps += tempMap
              }
              LuceneMedResults(parsedResult, luceneResAsMaps.toArray)
          }
      }
      catch 
      {
          case e1: JsonParseException => BadRequest(Map("message" -> "Unable to parse JSON"))
          case e2: MappingException => BadRequest(Map("message" -> "Unable to parse JSON"))
          case e3: JsonMappingException => BadRequest(Map("message" -> "Did not receive any content in the request body"))
      }
  }

  post("/diagnoses/diagnosisTextSearch")
  {
      logger.info("Received a post request")
      var parsedResult: String = null
      try 
      { 
          val userInput = request.body
          val extractedResult = parse(userInput).extract[DiagnosisFreeText]
          parsedResult = extractedResult.searchTerm
          try
          {
              val topResults = graphDB.getBestMatchTermForDiagnosisLookup(diagCxn, parsedResult, 10)
              if (topResults == None)
              {
                  val noContentMessage = "Your input of \"" + parsedResult + "\" returned no matches."
                  NoContent(Map("message" -> noContentMessage))
              }
              else
              {
                  var luceneResAsMaps = new ArrayBuffer[Map[String, String]]
                  for (a <- topResults.get)
                  {
                     var tempMap = Map("IRI" -> a(0), "label" -> a(1).replaceAll("@en", "").replaceAll("\"", ""))
                     luceneResAsMaps += tempMap
                  }
                  LuceneDiagResults(parsedResult, luceneResAsMaps.toArray)
              }
          }
          catch
          {
              case e: RuntimeException => InternalServerError(Map("message" -> "There was a problem retrieving results from the triplestore."))
          }
      }
      catch 
      {
          case e1: JsonParseException => BadRequest(Map("message" -> "Unable to parse JSON"))
          case e2: MappingException => BadRequest(Map("message" -> "Unable to parse JSON"))
          case e3: JsonMappingException => BadRequest(Map("message" -> "Did not receive any content in the request body"))
      }
  }

  post("/diagnoses/getAllDiagnosisMappingPaths")
  {
      logger.info("Received a post request")

      try
      { 
          DiagnosisPathways(graphDB.getDiagnosisMappingPathways(diagCxn))
      }
      catch
      {
          case e: RuntimeException => InternalServerError(Map("message" -> "There was a problem retrieving results from the triplestore."))
      }
  }

  post("/ontologies/getOmopConceptMap")
  {
      logger.info("Received a post request")
      try 
      { 

          val res: Map[String, String] = graphDB.getOmopConceptMap(ontCxn)
          OmopConceptMap(res)
      } 
      catch 
      {
          case e1: JsonParseException => BadRequest(Map("message" -> "Unable to parse JSON"))
          case e2: MappingException => BadRequest(Map("message" -> "Unable to parse JSON"))
          case e3: JsonMappingException => BadRequest(Map("message" -> "Did not receive any content in the request body"))
          case e4: NumberFormatException => BadRequest(Map("message" -> "The input receieved was not a valid integer"))
          case e5: RuntimeException => NoContent(Map("message" -> "Unknown internal server error"))
      }
  }

  /*post("/medications/findHopsAwayFromDrug")
  {
      logger.info("Received a post request")
      var parsedResult: Array[String] = null

      try 
      { 
          val userInput = request.body
          val extractedResult = parse(userInput).extract[DrugClassInputs]
          parsedResult = extractedResult.searchList
          logger.info("Input class: " + parsedResult)
          var g: GraphTraversalSource = null
          try 
          { 
              g = neo4jgraph.traversal()
              logger.info("Successfully connected to property graph")
            
              DrugHopsResults(neo4j.getHopsAwayFromTopLevelClass(parsedResult, "http://purl.obolibrary.org/obo/MONDO_0000001", g))
          }
          finally
          {
              g.close()
              logger.info("Connections closed.")
              println()
          }
      } 
      catch 
      {
          case e1: JsonParseException => BadRequest(Map("message" -> "Unable to parse JSON"))
          case e2: MappingException => BadRequest(Map("message" -> "Unable to parse JSON"))
          case e3: JsonMappingException => BadRequest(Map("message" -> "Did not receive any content in the request body"))
      }
  }*/
}