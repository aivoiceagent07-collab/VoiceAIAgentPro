
import React from 'react';
import { Globe, Heart, Zap } from 'lucide-react';

const MadeInIndia = () => {
  return (
    <section className="py-20 bg-gradient-to-r from-orange-50 via-white to-green-50">
      <div className="container mx-auto px-4">
        <div className="max-w-4xl mx-auto text-center">
          <div className="flex items-center justify-center mb-8 space-x-4">
            <div className="flex items-center space-x-2">
              <Heart className="h-8 w-8 text-orange-500" />
              <span className="text-2xl font-bold bg-gradient-to-r from-orange-500 to-green-600 bg-clip-text text-transparent">
                Made in India
              </span>
            </div>
            <Globe className="h-8 w-8 text-blue-600" />
            <span className="text-2xl font-bold text-gray-700">for the World</span>
          </div>
          
          <h2 className="text-4xl md:text-5xl font-bold text-gray-900 mb-8">
            Global Innovation, Indian Excellence
          </h2>
          
          <p className="text-xl text-gray-600 mb-12 leading-relaxed">
            Built with the precision of Indian engineering and the vision for global impact. 
            We combine deep technical expertise with cost-effective solutions that serve 
            organizations worldwide, bringing the best of Indian innovation to your business.
          </p>
          
          <div className="grid grid-cols-1 md:grid-cols-3 gap-8 mt-16">
            <div className="p-6 bg-white rounded-xl shadow-lg hover:shadow-xl transition-shadow duration-300">
              <div className="w-16 h-16 bg-gradient-to-r from-orange-500 to-red-500 rounded-full flex items-center justify-center mx-auto mb-4">
                <Zap className="h-8 w-8 text-white" />
              </div>
              <h3 className="text-xl font-bold text-gray-900 mb-3">Innovation Hub</h3>
              <p className="text-gray-600">
                Leveraging India's thriving tech ecosystem and world-class talent to build cutting-edge AI solutions.
              </p>
            </div>
            
            <div className="p-6 bg-white rounded-xl shadow-lg hover:shadow-xl transition-shadow duration-300">
              <div className="w-16 h-16 bg-gradient-to-r from-blue-500 to-purple-500 rounded-full flex items-center justify-center mx-auto mb-4">
                <Globe className="h-8 w-8 text-white" />
              </div>
              <h3 className="text-xl font-bold text-gray-900 mb-3">Global Reach</h3>
              <p className="text-gray-600">
                Serving clients across continents with 24/7 support and multilingual AI agents that understand diverse markets.
              </p>
            </div>
            
            <div className="p-6 bg-white rounded-xl shadow-lg hover:shadow-xl transition-shadow duration-300">
              <div className="w-16 h-16 bg-gradient-to-r from-green-500 to-blue-500 rounded-full flex items-center justify-center mx-auto mb-4">
                <Heart className="h-8 w-8 text-white" />
              </div>
              <h3 className="text-xl font-bold text-gray-900 mb-3">Cost Excellence</h3>
              <p className="text-gray-600">
                Delivering enterprise-grade solutions at a fraction of the cost, making AI accessible to businesses of all sizes.
              </p>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
};

export default MadeInIndia;
