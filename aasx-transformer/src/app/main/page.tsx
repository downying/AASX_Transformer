import React from 'react';
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

const MainPage = () => {
  return (
    <div className="flex flex-col items-center justify-center w-full h-full bg-background text-foreground">
      {/* 컨테이너 */}
      <Card className="w-full max-w-4xl shadow-lg">
        {/* 헤더 */}
        <CardHeader className="text-center">
          <CardTitle className="text-2xl md:text-3xl font-semibold">
            Upload the AASX package file
          </CardTitle>
          <p className="text-muted-foreground text-sm md:text-base">
            for URL download in JSON format
          </p>
        </CardHeader>

        {/* 업로드 영역 */}
        <CardContent>
          <div className="flex flex-col items-center justify-center p-6 border-2 border-dashed border-border rounded-lg bg-muted">
            <svg
              width="80"
              height="80"
              viewBox="0 0 100 100"
              fill="none"
              xmlns="http://www.w3.org/2000/svg"
            >
              <path
                d="M49.9999 45.8333V70.8333M37.4999 58.3333H62.4999M91.6666 79.1667C91.6666 81.3768 90.7886 83.4964 89.2258 85.0592C87.663 86.622 85.5434 87.5 83.3332 87.5H16.6666C14.4564 87.5 12.3368 86.622 10.774 85.0592C9.21123 83.4964 8.33325 81.3768 8.33325 79.1667V20.8333C8.33325 18.6232 9.21123 16.5036 10.774 14.9408C12.3368 13.378 14.4564 12.5 16.6666 12.5H37.4999L45.8333 25H83.3332C85.5434 25 87.663 25.878 89.2258 27.4408C90.7886 29.0036 91.6666 31.1232 91.6666 33.3333V79.1667Z"
                stroke="currentColor"
                strokeWidth="4"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            </svg>
            <p className="mt-3 text-lg font-medium">Drag and drop</p>
            <p className="text-sm text-muted-foreground">
              or click the button below to upload
            </p>
            <Button className="mt-4" variant="default">
              Upload
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  );
};

export default MainPage;
