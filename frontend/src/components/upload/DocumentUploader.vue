<template>
  <div class="document-uploader">
    <el-upload
      ref="uploadRef"
      class="upload-dragger"
      drag
      :auto-upload="false"
      :show-file-list="false"
      :limit="1"
      accept=".pdf,.docx,.txt,.md"
      :on-change="handleFileChange"
      :before-upload="handleBeforeUpload"
    >
      <el-icon class="upload-icon"><UploadFilled /></el-icon>
      <div class="upload-text">
        <p>拖拽文件至此或 <em>点击上传</em></p>
        <p class="upload-hint">支持 PDF、Word、TXT、Markdown 格式，最大 10MB</p>
      </div>
      <template #tip>
        <div class="format-tags">
          <el-tag size="small" type="info">PDF</el-tag>
          <el-tag size="small" type="success">Word (.docx)</el-tag>
          <el-tag size="small" type="warning">TXT</el-tag>
          <el-tag size="small" type="danger">Markdown</el-tag>
        </div>
      </template>
    </el-upload>

    <div v-if="currentFile" class="file-info">
      <el-icon><Document /></el-icon>
      <span class="file-name">{{ currentFile.name }}</span>
      <span class="file-size">{{ formatSize(currentFile.size) }}</span>

      <el-button
        v-if="!uploading"
        type="primary"
        size="small"
        @click="doUpload"
      >
        确认上传
      </el-button>

      <div v-else class="upload-progress-area">
        <el-progress
          :percentage="uploadProgress"
          :stroke-width="6"
          :status="uploadProgress >= 100 ? 'success' : undefined"
          style="flex: 1; max-width: 200px;"
        />
        <span class="progress-label">
          {{ uploadProgress >= 100 ? '处理中...' : `${uploadProgress}%` }}
        </span>
      </div>

      <el-button
        v-if="!uploading"
        size="small"
        circle
        :icon="Close"
        @click="clearFile"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { UploadFilled, Document, Close } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useDocumentStore } from '@/stores/document'
import type { UploadFile, UploadInstance } from 'element-plus'

const documentStore = useDocumentStore()
const uploadRef = ref<UploadInstance>()
const currentFile = ref<File | null>(null)
const uploading = ref(false)
const uploadProgress = ref(0)

function handleBeforeUpload(file: File): boolean {
  const validTypes = ['pdf', 'docx', 'txt', 'md']
  const ext = file.name.split('.').pop()?.toLowerCase()
  if (!ext || !validTypes.includes(ext)) {
    ElMessage.error(`不支持的文件格式: .${ext}`)
    return false
  }
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.error('文件大小不能超过 10MB')
    return false
  }
  return true
}

function handleFileChange(file: UploadFile) {
  if (file.raw) {
    currentFile.value = file.raw
  }
}

async function doUpload() {
  if (!currentFile.value || uploading.value) return

  uploading.value = true
  uploadProgress.value = 0

  try {
    await documentStore.uploadFile(currentFile.value, (progress) => {
      uploadProgress.value = progress
    })
    ElMessage.success(`"${currentFile.value.name}" 上传成功，已加入知识库`)
    clearFile()
  } catch (error: any) {
    let errorMsg = '上传失败'
    if (error?.response?.data?.message) {
      errorMsg = error.response.data.message
    } else if (error?.message) {
      if (error.message.includes('timeout') || error.code === 'ECONNABORTED') {
        errorMsg = '上传超时，请检查后端服务是否正常运行（Ollama/PGVector）'
      } else if (error.message.includes('Network Error')) {
        errorMsg = '网络错误，请检查后端服务是否已启动'
      } else {
        errorMsg = error.message
      }
    }
    ElMessage.error(errorMsg)
  } finally {
    uploading.value = false
    uploadProgress.value = 0
  }
}

function clearFile() {
  currentFile.value = null
  uploadRef.value?.clearFiles()
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(2) + ' MB'
}
</script>

<style scoped lang="scss">
.document-uploader {
  .upload-dragger {
    width: 100%;

    :deep(.el-upload-dragger) {
      padding: 24px;
      border-style: dashed;
      transition: all 0.3s;

      &:hover {
        border-color: #409eff;
        background: #f0f7ff;
      }
    }

    .upload-icon { font-size: 48px; color: #c0c4cc; margin-bottom: 8px; }

    .upload-text {
      p { margin: 0; font-size: 14px; color: #606266;
        em { color: #409eff; font-style: normal; }
      }
      .upload-hint { font-size: 12px; color: #999; margin-top: 8px; }
    }

    .format-tags {
      display: flex;
      gap: 6px;
      justify-content: center;
      margin-top: 12px;
    }
  }

  .file-info {
    margin-top: 16px;
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 12px;
    background: #f5f7fa;
    border-radius: 8px;

    .file-name {
      flex: 1;
      font-size: 13px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .file-size {
      font-size: 12px;
      color: #999;
    }

    .upload-progress-area {
      display: flex;
      align-items: center;
      gap: 8px;
      flex: 1;
      max-width: 260px;

      .progress-label {
        font-size: 12px;
        color: #909399;
        white-space: nowrap;
      }
    }
  }
}
</style>
