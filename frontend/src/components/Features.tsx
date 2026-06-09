
import React from 'react';
import { Card, CardContent } from '@/components/ui/card';
import { Phone, MessageSquare, Calendar, BarChart3, Clock, Users } from 'lucide-react';

const Features = () => {
  const features = [
    {
      icon: Phone,
      title: "Customer Service Automation",
      description: "Handle customer inquiries, complaints, and support tickets with AI agents that understand context and provide accurate solutions.",
      benefit: "Reduce support costs by 70%"
    },
    {
      icon: Calendar,
      title: "Appointment Scheduling",
      description: "Automate booking, rescheduling, and reminders across multiple calendars and time zones with natural conversation flow.",
      benefit: "Save 15 hours per week"
    },
    {
      icon: MessageSquare,
      title: "Sales & Lead Qualification",
      description: "Engage prospects, qualify leads, and schedule demos with AI agents trained on your sales methodology.",
      benefit: "Increase conversion by 40%"
    },
    {
      icon: BarChart3,
      title: "Data Collection & Analysis",
      description: "Gather customer feedback, conduct surveys, and analyze sentiment in real-time conversations.",
      benefit: "100% accurate data capture"
    },
    {
      icon: Clock,
      title: "24/7 Operations",
      description: "Never miss a call or inquiry with AI agents available round the clock across multiple languages.",
      benefit: "Zero missed opportunities"
    },
    {
      icon: Users,
      title: "Internal Communications",
      description: "Automate HR queries, IT support, and internal announcements with intelligent routing and responses.",
      benefit: "Boost team productivity"
    }
  ];

  return (
    <section className="py-20 bg-white" role="region" aria-labelledby="features-heading">
      <div className="container mx-auto px-4">
        <div className="text-center mb-16 animate-fade-in-up">
          <h2 id="features-heading" className="text-4xl md:text-5xl font-bold text-gray-900 mb-6">
            Transform Your Operations
          </h2>
          <p className="text-xl text-gray-600 max-w-3xl mx-auto">
            Our AI Voice Agents seamlessly integrate into your existing workflows, 
            handling complex tasks that traditionally require human intervention.
          </p>
        </div>
        
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
          {features.map((feature, index) => (
            <Card 
              key={index} 
              className={`group hover:shadow-xl transition-all duration-300 border-0 bg-gradient-to-br from-gray-50 to-white hover:from-blue-50 hover:to-purple-50 focus-within:ring-2 focus-within:ring-blue-500 focus-within:ring-offset-2 animate-fade-in-up animate-delay-${Math.min(600, 100 * (index + 1))}`}
              tabIndex={0}
              role="article"
              aria-labelledby={`feature-${index}-title`}
            >
              <CardContent className="p-8">
                <div className="flex items-center mb-6">
                  <div 
                    className="p-3 bg-gradient-to-r from-blue-600 to-purple-600 rounded-lg mr-4 group-hover:scale-110 transition-transform duration-300"
                    aria-hidden="true"
                  >
                    <feature.icon className="h-6 w-6 text-white" />
                  </div>
                  <div className="text-sm font-semibold text-green-600 bg-green-100 px-3 py-1 rounded-full">
                    {feature.benefit}
                  </div>
                </div>
                
                <h3 
                  id={`feature-${index}-title`}
                  className="text-xl font-bold text-gray-900 mb-4 group-hover:text-blue-600 transition-colors duration-300"
                >
                  {feature.title}
                </h3>
                
                <p className="text-gray-600 leading-relaxed">
                  {feature.description}
                </p>
              </CardContent>
            </Card>
          ))}
        </div>
      </div>
    </section>
  );
};

export default Features;
