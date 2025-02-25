import React from "react";
import Link from "next/link";  
import { Button } from "@/components/ui/button"; 

const Start = () => {
  return (
    <div className="flex flex-col items-center justify-center w-full h-full bg-background text-foreground">
      <div className="flex flex-col justify-center items-center gap-2">
        <div className="text-center text-[#1e1e1e] text-7xl font-bold font-['Inter'] leading-[86.40px]">
          Hello
        </div>
      </div>
      <div className="w-60 justify-center items-center gap-4 inline-flex mt-6">
        <Link href="/main" passHref>
          <Button className="mt-4 bg-blue-500 hover:bg-blue-400 w-40" variant="default">
            Start
          </Button>
        </Link>
      </div>
    </div>
  );
};

export default Start;
