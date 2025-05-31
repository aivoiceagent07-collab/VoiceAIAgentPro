
import React from 'react';
import { Button } from '@/components/ui/button';
import { ArrowRight, Mic, Zap } from 'lucide-react';

const Hero = () => {
  return (
    <section 
      className="relative min-h-screen bg-gradient-to-br from-blue-50 via-white to-purple-50 flex items-center justify-center px-4"
      role="banner"
      aria-label="AI Voice Agents Hero Section"
    >
      <div className="absolute inset-0 bg-grid-pattern opacity-5" aria-hidden="true"></div>
      <div className="container mx-auto text-center relative z-10">
        <div className="max-w-4xl mx-auto">
          <div className="flex items-center justify-center mb-6 space-x-2 animate-fade-in-up">
            <Mic className="h-8 w-8 text-blue-600 animate-pulse" aria-hidden="true" />
            <Zap className="h-6 w-6 text-purple-600" aria-hidden="true" />
          </div>
          
          <h1 className="text-5xl md:text-7xl font-bold bg-gradient-to-r from-blue-600 to-purple-600 bg-clip-text text-transparent mb-6 leading-tight animate-fade-in-up animate-delay-200">
            AI Voice Agents
          </h1>
          
          <h2 className="text-2xl md:text-3xl text-gray-700 mb-8 font-light animate-fade-in-up animate-delay-300">
            Revolutionize Your Organization with Intelligent Voice Automation
          </h2>
          
          <p className="text-xl text-gray-600 mb-12 max-w-3xl mx-auto leading-relaxed animate-fade-in-up animate-delay-400">
            Cut operational costs by up to 60% while boosting productivity. Our AI Voice Agents handle customer service, 
            appointments, sales calls, and internal communications with human-like intelligence.
          </p>
          
          <div className="flex flex-col sm:flex-row gap-4 justify-center items-center animate-fade-in-up animate-delay-500">
            <Button 
              size="lg" 
              className="bg-gradient-to-r from-blue-600 to-purple-600 hover:from-blue-700 hover:to-purple-700 text-white px-8 py-4 text-lg rounded-full transition-all duration-300 transform hover:scale-105 focus-outline"
              aria-label="Get started with AI Voice Agents today"
            >
              Get Started Today
              <ArrowRight className="ml-2 h-5 w-5" aria-hidden="true" />
            </Button>
            
            <Button 
              variant="outline" 
              size="lg"
              className="border-2 border-gray-300 text-gray-700 hover:bg-gray-50 px-8 py-4 text-lg rounded-full transition-all duration-300 focus-outline"
              aria-label="Watch demonstration video"
            >
              Watch Demo
            </Button>
          </div>
          
          <div className="mt-16 grid grid-cols-1 md:grid-cols-3 gap-8 text-center" role="region" aria-label="Key statistics">
            <div className="p-6 animate-slide-in-left animate-delay-600">
              <div className="text-3xl font-bold text-blue-600 mb-2" aria-label="60 percent cost reduction">60%</div>
              <div className="text-gray-600">Cost Reduction</div>
            </div>
            <div className="p-6 animate-fade-in animate-delay-700">
              <div className="text-3xl font-bold text-purple-600 mb-2" aria-label="24/7 availability">24/7</div>
              <div className="text-gray-600">Availability</div>
            </div>
            <div className="p-6 animate-slide-in-right animate-delay-600">
              <div className="text-3xl font-bold text-green-600 mb-2" aria-label="5 times efficiency boost">5x</div>
              <div className="text-gray-600">Efficiency Boost</div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
};

export default Hero;
