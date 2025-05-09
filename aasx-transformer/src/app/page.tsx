import React from "react";
import Link from "next/link";
import { Button } from "@/components/ui/button";

// 초기 화면 
const Start = () => {
  return (
    <div className="flex flex-col items-center justify-center w-full h-full bg-background text-foreground">
      <div className="flex flex-col justify-center items-center gap-2">
        <div className="text-center text-[#1e1e1e] text-7xl font-bold font-['Inter'] leading-[86.40px]">
          Hello
        </div>
      </div>
      <div className="w-full justify-center items-center gap-6 inline-flex mt-6 flex-col">
        {/* Upload AASX 버튼 */}
        <Link href="/main" passHref>
          <Button className="mt-4 bg-white text-black border border-black hover:bg-black hover:text-white w-[300px] text-lg" variant="default">
            Upload AASX
          </Button>
        </Link>

        {/* Upload JSON 버튼 */}
        <Link href="/main/uploadJSON" passHref>
          <Button className="mt-4 bg-white text-black border border-black hover:bg-black hover:text-white w-[300px] text-lg" variant="default">
            Upload JSON
          </Button>
        </Link>
      </div>
    </div>
  );
};

export default Start;
