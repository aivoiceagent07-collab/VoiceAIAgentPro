
import React from 'react';
import Logo from './Logo';
import { Button } from '@/components/ui/button';

const Header = () => {
  return (
    <header 
      className="fixed top-0 w-full bg-white/90 backdrop-blur-md border-b border-gray-200 z-50"
      role="banner"
    >
      <div className="container mx-auto px-4 py-4">
        <div className="flex items-center justify-between">
          <Logo size="md" />
          
          <nav className="hidden md:flex items-center space-x-8" role="navigation" aria-label="Main navigation">
            <a href="#features" className="text-gray-700 hover:text-blue-600 transition-colors duration-200 focus-outline">
              Features
            </a>
            <a href="#about" className="text-gray-700 hover:text-blue-600 transition-colors duration-200 focus-outline">
              About
            </a>
            <a href="#contact" className="text-gray-700 hover:text-blue-600 transition-colors duration-200 focus-outline">
              Contact
            </a>
          </nav>
          
          <Button 
            className="bg-gradient-to-r from-blue-600 to-purple-600 hover:from-blue-700 hover:to-purple-700 text-white focus-outline"
            aria-label="Get started with AI Voice Agents"
          >
            Get Started
          </Button>
        </div>
      </div>
    </header>
  );
};

export default Header;
