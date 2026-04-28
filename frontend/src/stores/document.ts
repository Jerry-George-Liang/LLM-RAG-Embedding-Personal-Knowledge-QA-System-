import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { DocumentMetadata } from '@/types'
import request from '@/api/request'

export const useDocumentStore = defineStore('document', () => {
  const documents = ref<DocumentMetadata[]>([])
  const uploading = ref(false)
  const uploadProgress = ref(0)

  async function fetchDocuments() {
    const res: any = await request.get('/documents/list')
    documents.value = res.data || []
  }

  async function uploadFile(file: File, onProgress?: (progress: number) => void) {
    uploading.value = true
    uploadProgress.value = 0
    const formData = new FormData()
    formData.append('file', file)
    try {
      const res: any = await request.post('/documents/upload', formData, {
        onUploadProgress: (progressEvent) => {
          if (progressEvent.total && progressEvent.total > 0) {
            const percent = Math.round((progressEvent.loaded * 100) / progressEvent.total)
            uploadProgress.value = percent
            onProgress?.(percent)
          }
        }
      })
      await fetchDocuments()
      return res.data
    } finally {
      uploading.value = false
      uploadProgress.value = 0
    }
  }

  async function clearAllDocuments() {
    await request.delete('/documents/clear')
    documents.value = []
  }

  async function deleteDocument(documentId: string) {
    await request.delete(`/documents/${documentId}`)
    documents.value = documents.value.filter(doc => doc.documentId !== documentId)
  }

  return {
    documents,
    uploading,
    uploadProgress,
    fetchDocuments,
    uploadFile,
    clearAllDocuments,
    deleteDocument
  }
})
