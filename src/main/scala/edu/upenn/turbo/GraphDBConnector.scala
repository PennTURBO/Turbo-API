package edu.upenn.turbo

import java.io.File
import org.neo4j.graphdb._
import org.neo4j.graphdb.factory.GraphDatabaseFactory
import org.apache.tinkerpop.gremlin.neo4j.structure.Neo4jGraph
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.out
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.in
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.has
import org.apache.tinkerpop.gremlin.process.traversal.P._
import org.eclipse.rdf4j.query.QueryLanguage
import org.eclipse.rdf4j.query.TupleQuery
import org.eclipse.rdf4j.query.TupleQueryResult
import org.eclipse.rdf4j.repository.RepositoryConnection
import org.eclipse.rdf4j.query.BooleanQuery
import org.eclipse.rdf4j.query.BindingSet
import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.repository.Repository
import org.eclipse.rdf4j.OpenRDFException
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource
import scala.collection.mutable.ArrayBuffer

import java.nio.file.Path
import java.nio.file.Paths
import org.slf4j.LoggerFactory

class GraphDBConnector 
{
    val logger = LoggerFactory.getLogger("turboAPIlogger")

    def getDiseaseURIs(startingCodes: Array[String], cxn: RepositoryConnection): Array[Array[String]] =
    {
        var startListAsString = ""
        for (code <- startingCodes) startListAsString += " <" + code + "> "
        logger.info("launching query to Graph DB")
        val query = s"""
            PREFIX obo: <http://purl.obolibrary.org/obo/>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            PREFIX owl: <http://www.w3.org/2002/07/owl#>
            PREFIX oboInOwl: <http://www.geneontology.org/formats/oboInOwl#>
            PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
            PREFIX j.0: <http://example.com/resource/>
            PREFIX snomed: <http://purl.bioontology.org/ontology/SNOMEDCT/>
            select distinct ?icd ?mondo ?mlabel ?method where
            {
                values ?icd {$startListAsString}
                graph ?g 
                {
                    ?mondo <http://graphBuilder.org/mapsTo> ?icd .
                }
                graph obo:mondo.owl
                {
                    ?mondo rdfs:label ?mlabel .
                }
                ?g <http://graphBuilder.org/usedMethod> ?method .
            }
            order by ?icd ?mondo"""

        val tupleQueryResult = cxn.prepareTupleQuery(QueryLanguage.SPARQL, query).evaluate()
        val resultList: ArrayBuffer[Array[String]] = new ArrayBuffer[Array[String]]
        while (tupleQueryResult.hasNext()) 
        {
            val bindingset: BindingSet = tupleQueryResult.next()
            var icdSub: String = bindingset.getValue("icd").toString
            var mondoSub: String = bindingset.getValue("mondo").toString
            var mondoLabel: String = bindingset.getValue("mlabel").toString
            var method: String = bindingset.getValue("method").toString
            //logger.info(icdSub + " " + mondoSub + " " + mondoLabel + " " + method)
            resultList += Array(icdSub, mondoSub, mondoLabel, method)
        }
        logger.info("result size: " + resultList.size)
        resultList.toArray
    }

    def getDiagnosisCodes(start: String, cxn: RepositoryConnection): Array[String] =
    {
        val query = """
            PREFIX obo: <http://purl.obolibrary.org/obo/>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            PREFIX owl: <http://www.w3.org/2002/07/owl#>
            PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
            PREFIX snomed: <http://purl.bioontology.org/ontology/SNOMEDCT/>
            PREFIX umls: <http://bioportal.bioontology.org/ontologies/umls/>
            PREFIX oboInOwl: <http://www.geneontology.org/formats/oboInOwl#>
            PREFIX turbo: <http://transformunify.org/ontologies/>
            select
            distinct ?icdsub
            where {
                values ?mondostart {
                    <"""+start+""">
                }
                graph obo:mondo.owl {
                    ?mondostart rdfs:label ?mlab1 .
                    ?mondosub rdfs:subClassOf* ?mondostart ;
                                             rdfs:label ?mlab2 .
                }
                graph <http://graphBuilder.org/mondoToIcdMappingsFullSemantics>
                {
                    ?mondosub <http://graphBuilder.org/mapsTo> ?icdsub .
                }
            }
        """
        val tupleQueryResult = cxn.prepareTupleQuery(QueryLanguage.SPARQL, query).evaluate()
        val resultList: ArrayBuffer[String] = new ArrayBuffer[String]
        while (tupleQueryResult.hasNext()) 
        {
            val bindingset: BindingSet = tupleQueryResult.next()
            var result: String = bindingset.getValue("icdsub").toString
            resultList += result
        }
        logger.info("result size: " + resultList.size)
        resultList.toArray
    }

    def getBestMatchTermForMedicationLookup(cxn: RepositoryConnection, userInput: String, limit: Integer = 1): Option[ArrayBuffer[ArrayBuffer[String]]] =
    {
          val query = """
              PREFIX : <http://www.ontotext.com/connectors/lucene#>
              PREFIX inst: <http://www.ontotext.com/connectors/lucene/instance#>
              PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
              PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
              SELECT ?entity ?score ?label {
                  ?search a inst:role_via_rdfs_or_skos_label ;
                          :query "role_via_rdfs_label:"""+userInput+""" OR role_via_skos_label:"""+userInput+"""" ;
                                                             :entities ?entity .
                  ?entity :score ?score .
                  {
                      {
                          graph <http://data.bioontology.org/ontologies/RXNORM/submissions/15/download> 
                          {
                              ?entity skos:prefLabel ?label .
                          }
                      }
                      union
                      {
                          graph <ftp://ftp.ebi.ac.uk/pub/databases/chebi/ontology/chebi_lite.owl.gz>
                          {
                              ?entity rdfs:label ?label .
                          }
                      }
                  }
              }
              order by desc(?score)
              limit 
          """ + limit

          val tupleQueryResult = cxn.prepareTupleQuery(QueryLanguage.SPARQL, query).evaluate()
          var buffResults = new ArrayBuffer[ArrayBuffer[String]]
          while (tupleQueryResult.hasNext()) 
          {
              val nextResult = tupleQueryResult.next
              val singleResult = ArrayBuffer(nextResult.getValue("entity").toString, nextResult.getValue("label").toString)
              buffResults += singleResult
          }
          if (buffResults.size != 0) Some(buffResults)
          else None
      }

      def getBestMatchTermForDiagnosisLookup(cxn: RepositoryConnection, userInput: String, limit: Integer = 1): Option[ArrayBuffer[ArrayBuffer[String]]] =
      {
          val query = """
              PREFIX : <http://www.ontotext.com/connectors/lucene#>
              PREFIX inst: <http://www.ontotext.com/connectors/lucene/instance#>
              PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
              PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
              SELECT ?entity ?score ?label {
                  ?search a inst:MONDO_labelsAndSynonyms ;
                          :query "mondoLabel:"""+userInput+""" OR mondoExactSynonym:"""+userInput+"""" ;
                                                     :entities ?entity .
                  ?entity :score ?score .
                  ?entity rdfs:label ?label .
              }
              order by desc(?score)
              limit """ + limit

          val tupleQueryResult = cxn.prepareTupleQuery(QueryLanguage.SPARQL, query).evaluate()
          var buffResults = new ArrayBuffer[ArrayBuffer[String]]
          while (tupleQueryResult.hasNext()) 
          {
              val nextResult = tupleQueryResult.next
              val singleResult = ArrayBuffer(nextResult.getValue("entity").toString, nextResult.getValue("label").toString)
              buffResults += singleResult
          }
          if (buffResults.size != 0) Some(buffResults)
          else None
      }
}