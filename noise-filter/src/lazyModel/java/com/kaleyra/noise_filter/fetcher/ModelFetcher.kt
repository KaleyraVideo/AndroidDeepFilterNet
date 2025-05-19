package fetcher

import java.io.File

/**
 * An interface for components responsible for fetching and downloading a model.
 */
interface ModelFetcher {
    suspend fun fetchModel(destinationFile: File): Boolean
}