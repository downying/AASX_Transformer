"use client"

import React from "react"
import Link from "next/link"
import { Card, CardContent, CardTitle } from "@/components/ui/card"
import { UploadCloud, FileText } from "lucide-react"

export default function AdminPage() {

  return (
    <div className="p-6 flex flex-col md:flex-row justify-center items-center gap-40">
      {/* 업로드 화면으로 이동하는 카드 */}
      <Link href="/admin/uploaded">
        <Card className="w-48 h-48 bg-blue-100 hover:bg-blue-200 cursor-pointer rounded-xl border shadow text-card-foreground">
          <CardContent className="p-0 flex flex-col items-center justify-center space-y-2 h-full">
            <UploadCloud className="w-12 h-12 text-blue-600" />
            <CardTitle className="text-lg font-bold text-gray-800 text-center">
              Uploaded Files
            </CardTitle>
          </CardContent>
        </Card>
      </Link>

      {/* 파일 메타 화면으로 이동하는 카드 */}
      <Link href="/admin/file-meta">
        <Card className="w-48 h-48 bg-green-100 hover:bg-green-200 cursor-pointer rounded-xl border shadow text-card-foreground">
          <CardContent className="p-0 flex flex-col items-center justify-center space-y-2 h-full">
            <FileText className="w-12 h-12 text-green-600" />
            <CardTitle className="text-lg font-bold text-gray-800 text-center">
              View File<br/>Metadata
            </CardTitle>
          </CardContent>
        </Card>
      </Link>
    </div>
  )
}
