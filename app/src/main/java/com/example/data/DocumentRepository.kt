package com.example.data

import kotlinx.coroutines.flow.Flow

class DocumentRepository(private val documentDao: DocumentDao) {
    val allDocuments: Flow<List<DocumentEntity>> = documentDao.getAllDocuments()
    val allFolders: Flow<List<FolderEntity>> = documentDao.getAllFolders()

    fun getDocumentById(id: String): Flow<DocumentEntity?> = documentDao.getDocumentById(id)

    suspend fun getDocumentByIdSync(id: String): DocumentEntity? = documentDao.getDocumentByIdSync(id)

    suspend fun saveDocument(document: DocumentEntity) {
        documentDao.insertDocument(document)
    }

    suspend fun updateDocument(document: DocumentEntity) {
        documentDao.updateDocument(document)
    }

    suspend fun deleteDocument(document: DocumentEntity) {
        documentDao.deleteDocument(document)
    }

    suspend fun renameDocument(id: String, newName: String) {
        documentDao.renameDocument(id, newName, System.currentTimeMillis())
    }

    suspend fun moveDocumentToFolder(id: String, folderId: Long?) {
        documentDao.moveDocumentToFolder(id, folderId, System.currentTimeMillis())
    }

    suspend fun createFolder(name: String): Long {
        return documentDao.insertFolder(FolderEntity(name = name))
    }

    suspend fun deleteFolder(id: Long) {
        documentDao.deleteFolder(id)
    }

    suspend fun getFolderById(id: Long): FolderEntity? {
        return documentDao.getFolderById(id)
    }
}
