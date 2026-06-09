
import React from 'react';
import { Heart, Globe, Mail, Phone, MapPin } from 'lucide-react';

const Footer = () => {
  return (
    <footer 
      className="bg-gradient-to-r from-gray-900 to-gray-800 text-white py-12"
      role="contentinfo"
      aria-label="Website footer"
    >
      <div className="container mx-auto px-4">
        <div className="grid grid-cols-1 md:grid-cols-3 gap-8 mb-8">
          <div className="animate-fade-in-up">
            <h3 className="text-2xl font-bold mb-4 flex items-center">
              <Heart className="h-6 w-6 text-orange-500 mr-2 animate-bounce-gentle" aria-hidden="true" />
              AI Voice Agents
            </h3>
            <p className="text-gray-300 mb-4">
              Transforming organizations worldwide with intelligent voice automation. 
              Made in India for the World.
            </p>
            <div className="flex items-center text-sm text-gray-400">
              <Globe className="h-4 w-4 mr-2" aria-hidden="true" />
              <span>Serving clients globally from India</span>
            </div>
          </div>
          
          <div className="animate-fade-in-up animate-delay-200">
            <h4 className="text-lg font-semibold mb-4">Solutions</h4>
            <nav aria-label="Solutions menu">
              <ul className="space-y-2 text-gray-300">
                <li><a href="#" className="hover:text-white transition-colors duration-200 focus-outline">Customer Service Automation</a></li>
                <li><a href="#" className="hover:text-white transition-colors duration-200 focus-outline">Sales & Lead Qualification</a></li>
                <li><a href="#" className="hover:text-white transition-colors duration-200 focus-outline">Appointment Scheduling</a></li>
                <li><a href="#" className="hover:text-white transition-colors duration-200 focus-outline">Internal Communications</a></li>
                <li><a href="#" className="hover:text-white transition-colors duration-200 focus-outline">Data Collection & Analysis</a></li>
              </ul>
            </nav>
          </div>
          
          <div className="animate-fade-in-up animate-delay-300">
            <h4 className="text-lg font-semibold mb-4">Contact Info</h4>
            <address className="space-y-3 text-gray-300 not-italic">
              <div className="flex items-center">
                <Mail className="h-4 w-4 mr-3 text-blue-400" aria-hidden="true" />
                <a 
                  href="mailto:hello@aivoiceagents.com" 
                  className="hover:text-white transition-colors duration-200 focus-outline"
                  aria-label="Email us at hello@aivoiceagents.com"
                >
                  hello@aivoiceagents.com
                </a>
              </div>
              <div className="flex items-center">
                <Phone className="h-4 w-4 mr-3 text-green-400" aria-hidden="true" />
                <a 
                  href="tel:+918001234567" 
                  className="hover:text-white transition-colors duration-200 focus-outline"
                  aria-label="Call us at +91 800 123 4567"
                >
                  +91 (800) 123-4567
                </a>
              </div>
              <div className="flex items-center">
                <MapPin className="h-4 w-4 mr-3 text-red-400" aria-hidden="true" />
                <span>Bangalore, India</span>
              </div>
            </address>
          </div>
        </div>
        
        <div className="border-t border-gray-700 pt-8 text-center text-gray-400 animate-fade-in animate-delay-400">
          <p>&copy; 2024 AI Voice Agents. Made with <span className="text-red-500" aria-label="love">❤️</span> in India for the World.</p>
        </div>
      </div>
    </footer>
  );
};

export default Footer;
