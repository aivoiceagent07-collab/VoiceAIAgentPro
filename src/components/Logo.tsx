
import React from 'react';
import { Mic, Globe, Heart, Zap } from 'lucide-react';

interface LogoProps {
  size?: 'sm' | 'md' | 'lg';
  className?: string;
}

const Logo = ({ size = 'md', className = '' }: LogoProps) => {
  const sizes = {
    sm: {
      container: 'text-lg',
      icons: 'h-4 w-4',
      text: 'text-sm'
    },
    md: {
      container: 'text-xl',
      icons: 'h-5 w-5',
      text: 'text-base'
    },
    lg: {
      container: 'text-2xl',
      icons: 'h-6 w-6',
      text: 'text-lg'
    }
  };

  const currentSize = sizes[size];

  return (
    <div className={`flex items-center space-x-2 ${currentSize.container} ${className}`} role="img" aria-label="AI Voice Agents - Made in India for the World">
      <div className="flex items-center space-x-1">
        <div className="relative">
          <Mic className={`${currentSize.icons} text-blue-600`} aria-hidden="true" />
          <Zap className="h-3 w-3 text-purple-500 absolute -top-1 -right-1" aria-hidden="true" />
        </div>
        <span className="font-bold bg-gradient-to-r from-blue-600 to-purple-600 bg-clip-text text-transparent">
          AI Voice
        </span>
      </div>
      
      <div className="flex items-center space-x-1">
        <Heart className={`${currentSize.icons} text-orange-500`} aria-hidden="true" />
        <span className="font-semibold text-orange-600">India</span>
      </div>
      
      <div className="flex items-center space-x-1">
        <Globe className={`${currentSize.icons} text-green-600`} aria-hidden="true" />
        <span className="font-semibold text-green-700">World</span>
      </div>
    </div>
  );
};

export default Logo;
